"""Local, opt-in OCR worker. No database access. Source URLs restricted to posting image hosts."""
import hashlib
import io
import json
import math
import os
import socket
import ipaddress
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, HTTPServer

from PIL import Image, ImageOps

HOSTS = frozenset({"www.saraminimage.co.kr", "saraminimage.co.kr", "static.saraminimage.co.kr"})
MAX_BYTES = 12_000_000
MAX_PIXELS = 24_000_000
MAX_LINES = 3000
TILE_HEIGHT = 1600
OVERLAP = 160
MODEL = "PP-OCRv5_server_det/korean_PP-OCRv5_mobile_rec;tiles1600-overlap160-upscale2-det1280-batch1-v2"
Image.MAX_IMAGE_PIXELS = MAX_PIXELS
CACHE = OrderedDict()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def fetch_image(url):
    uri = urllib.parse.urlsplit(url)
    if uri.scheme != "https" or uri.hostname not in HOSTS or uri.username or uri.password or uri.port not in (None, 443):
        raise ValueError("unsupported image URL")
    # Fixed public CDN hosts only; reject private/loopback DNS responses too.
    for item in socket.getaddrinfo(uri.hostname, 443, type=socket.SOCK_STREAM):
        if not ipaddress.ip_address(item[4][0]).is_global:
            raise ValueError("non-public image address")
    req = urllib.request.Request(url, headers={"User-Agent": "SejongCompass/0.1 (posting image OCR)"})
    with urllib.request.build_opener(NoRedirect).open(req, timeout=25) as response:
        if not response.headers.get("Content-Type", "").lower().startswith("image/"):
            raise ValueError("not an image")
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("image byte limit exceeded")
    return data


def decode(data):
    with Image.open(io.BytesIO(data)) as source:
        if source.width * source.height > MAX_PIXELS or source.width > 4000 or source.height > 24000:
            raise ValueError("image dimension limit exceeded")
        image = ImageOps.exif_transpose(source).convert("RGBA")
        white = Image.new("RGBA", image.size, "white")
        white.alpha_composite(image)
        return white.convert("RGB")


def tile_plan(width, height):
    """Vertical overlap keeps text cut by a boundary available in the adjacent tile."""
    if width < 1 or height < 1:
        raise ValueError("invalid dimensions")
    top = 0
    while top < height:
        bottom = min(top + TILE_HEIGHT, height)
        yield (0, top, width, bottom)
        if bottom == height:
            break
        top = bottom - OVERLAP


def same_detection(a, b):
    if "".join(a["text"].split()) != "".join(b["text"].split()):
        return False
    ax, ay, ar, ab = a["box"]
    bx, by, br, bb = b["box"]
    intersection = max(0, min(ar, br) - max(ax, bx)) * max(0, min(ab, bb) - max(ay, by))
    area = min((ar - ax) * (ab - ay), (br - bx) * (bb - by))
    return area > 0 and intersection / area > 0.5


def recognize(image, engine):
    import numpy as np
    if image.width < 32 or image.height < 12:
        return []
    lines = []
    deadline = time.monotonic() + 480
    for left, top, right, bottom in tile_plan(*image.size):
        if time.monotonic() > deadline:
            raise TimeoutError("OCR processing time limit exceeded")
        tile = image.crop((left, top, right, bottom))
        scale = min(2.0, 1600 / tile.width) if tile.width < 1200 else 1.0
        if scale != 1:
            tile = tile.resize((round(tile.width * scale), round(tile.height * scale)), Image.Resampling.LANCZOS)
        for result in engine.predict(np.asarray(tile)):
            for text, score, polygon in zip(result["rec_texts"], result["rec_scores"], result["rec_polys"]):
                text = str(text).strip()
                score = float(score)
                if not text:
                    continue
                if len(text) > 4000 or not math.isfinite(score):
                    raise ValueError("invalid OCR output")
                xs = [float(point[0]) / scale + left for point in polygon]
                ys = [float(point[1]) / scale + top for point in polygon]
                candidate = {"text": text, "confidence": score,
                             "box": [max(0, min(xs)), max(0, min(ys)), min(image.width, max(xs)), min(image.height, max(ys))]}
                duplicate = next((i for i, old in enumerate(lines) if same_detection(old, candidate)), None)
                if duplicate is None:
                    lines.append(candidate)
                elif candidate["confidence"] > lines[duplicate]["confidence"]:
                    lines[duplicate] = candidate
                if len(lines) > MAX_LINES:
                    raise ValueError("OCR line limit exceeded")
    # Reading order is approximate; retain boxes because this is NOT table structure recognition.
    return sorted(lines, key=lambda line: (line["box"][1], line["box"][0]))


def process(data, engine):
    key = hashlib.sha256(data).hexdigest()
    if key in CACHE:
        CACHE.move_to_end(key)
        return CACHE[key]
    reply = {"model": MODEL, "lines": recognize(decode(data), engine)}
    CACHE[key] = reply
    if len(CACHE) > 16:
        CACHE.popitem(last=False)
    return reply


def serve():
    from paddleocr import PaddleOCR
    engine = PaddleOCR(lang="korean", enable_mkldnn=False, cpu_threads=2,
                       text_det_limit_side_len=1280, text_det_limit_type="max", text_recognition_batch_size=1,
                       use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False)

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/ocr":
                self.send_error(404)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length < 1 or length > 8192:
                    raise ValueError("invalid request size")
                request = json.loads(self.rfile.read(length))
                reply = process(fetch_image(request["url"]), engine)
                output = json.dumps(reply, ensure_ascii=False).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(output)))
                self.end_headers()
                self.wfile.write(output)
            except (ValueError, KeyError, OSError, TimeoutError):
                self.send_error(422, "image OCR failed")
            except Exception:
                # No URL, source text or stack trace in public error responses.
                self.send_error(500, "OCR unavailable")

    # Container port must be published on 127.0.0.1 only, as documented.
    HTTPServer((os.environ.get("OCR_BIND", "127.0.0.1"), 8091), Handler).serve_forever()


if __name__ == "__main__":
    serve()



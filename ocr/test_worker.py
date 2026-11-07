import io
import unittest
from PIL import Image
from unittest.mock import patch
from worker import decode, tile_plan, recognize, same_detection, process, CACHE, fetch_image


class WorkerTests(unittest.TestCase):
    def test_long_image_coverage_and_overlap(self):
        plan = list(tile_plan(835, 5058))
        self.assertEqual(plan[0], (0, 0, 835, 1600))
        self.assertEqual(plan[-1][3], 5058)
        self.assertTrue(all(a[3] - b[1] == 160 for a, b in zip(plan, plan[1:])))
        self.assertTrue(all(box[3] - box[1] <= 1600 for box in plan))

    def test_transparency_white_and_dimensions(self):
        stream = io.BytesIO()
        Image.new("RGBA", (100, 100), (0, 0, 0, 0)).save(stream, "PNG")
        self.assertEqual(decode(stream.getvalue()).getpixel((0, 0)), (255, 255, 255))

    def test_private_and_redirect_targets_are_not_supported(self):
        for url in ["http://www.saraminimage.co.kr/a.png", "https://127.0.0.1/a", "https://evil.test/a",
                    "https://www.saraminimage.co.kr:444/a", "https://user@www.saraminimage.co.kr/a"]:
            with self.assertRaises(ValueError):
                fetch_image(url)

    def test_overlapping_duplicates_only(self):
        a = {"text": "같은 문구", "box": [0, 100, 100, 120]}
        self.assertTrue(same_detection(a, {"text": "같은문구", "box": [0, 101, 100, 121]}))
        self.assertFalse(same_detection(a, {"text": "같은 문구", "box": [0, 500, 100, 520]}))

    def test_small_text_upscaled_and_coordinates_restored(self):
        class Engine:
            def predict(self, array):
                self.shape = array.shape
                return [{"rec_texts": ["표의 작은 글씨"], "rec_scores": [0.9],
                         "rec_polys": [[[20, 40], [120, 40], [120, 80], [20, 80]]]}]
        engine = Engine()
        lines = recognize(Image.new("RGB", (600, 100)), engine)
        self.assertEqual(engine.shape[:2], (200, 1200))
        self.assertEqual(lines[0]["box"], [10, 20, 60, 40])

    def test_content_cache_prevents_repeated_inference(self):
        CACHE.clear()
        stream = io.BytesIO()
        Image.new("RGB", (50, 50)).save(stream, "PNG")
        with patch("worker.recognize", return_value=[]) as call:
            process(stream.getvalue(), None)
            process(stream.getvalue(), None)
            self.assertEqual(call.call_count, 1)

    def test_invalid_image_fails(self):
        with self.assertRaises(OSError):
            decode(b"not an image")


if __name__ == "__main__":
    unittest.main()


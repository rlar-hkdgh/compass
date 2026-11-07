package com.example.compass.recruit.collection.ocr;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Component
public class PostingBodyOcr {
    @FunctionalInterface interface Engine { BodyOcrClient.Reply recognize(String url) throws IOException, InterruptedException; }
    public record Evidence(int imageIndex, String url, String status, String model, List<BodyOcrClient.Line> lines) {}
    public record Result(String html, List<Evidence> evidence) {}
    private final boolean enabled;
    private final Engine engine;
    @org.springframework.beans.factory.annotation.Autowired
    public PostingBodyOcr(BodyOcrClient client, @Value("${compass.recruit.ocr.enabled:false}") boolean enabled) {
        this(enabled, client::recognize);
    }
    PostingBodyOcr(boolean enabled, Engine engine) { this.enabled = enabled; this.engine = engine; }
    public Result supplement(String html, String bodyUrl) throws InterruptedException {
        var doc = Jsoup.parse(html, bodyUrl);
        var content = doc.selectFirst(".user_content");
        if (content == null) content = doc.body();
        content.select("script,style,noscript,iframe,button").remove();
        List<Evidence> evidence = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0, attempts = 0;
        for (var img : content.select("img")) {
            String url = img.absUrl("src");
            String status = !enabled ? "DISABLED" : !allowed(url) ? "UNSUPPORTED_URL" : null;
            BodyOcrClient.Reply reply = null;
            if (status == null) {
                if (!seen.add(url)) status = "DUPLICATE";
                else if (attempts >= 20) status = "LIMIT";
                else {
                    attempts++;
                    try {
                        reply = engine.recognize(url);
                        status = reply.lines().isEmpty() ? "NO_TEXT" : "SUCCESS";
                    } catch (IOException | RuntimeException ex) { status = "FAILED"; }
                }
            }
            if (reply != null && !reply.lines().isEmpty()) {
                // text() escapes OCR output; OCR text can never introduce HTML or scripts.
                Element block = new Element("div").attr("data-compass-ocr", Integer.toString(index));
                block.text(reply.lines().stream().map(BodyOcrClient.Line::text)
                    .collect(java.util.stream.Collectors.joining("\n")));
                img.replaceWith(block);
            }
            evidence.add(new Evidence(index++, url, status, reply == null ? null : reply.model(),
                reply == null ? List.of() : reply.lines()));
        }
        return new Result(content.outerHtml(), List.copyOf(evidence));
    }
    static boolean allowed(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && Set.of("www.saraminimage.co.kr", "saraminimage.co.kr", "static.saraminimage.co.kr").contains(uri.getHost());
        } catch (IllegalArgumentException ex) { return false; }
    }
}




package com.example.compass.recruit.collection.ocr;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

@Component
public class BodyOcrClient {
    public record Line(String text, double confidence, List<Double> box) {}
    public record Reply(String model, List<Line> lines) {}
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();
    public BodyOcrClient(@Value("${compass.recruit.ocr.endpoint:http://127.0.0.1:8091/ocr}") String endpoint) {
        this.endpoint = URI.create(endpoint);
        if (!List.of("127.0.0.1", "localhost", "::1", "[::1]").contains(this.endpoint.getHost())
                || !"http".equals(this.endpoint.getScheme()) || this.endpoint.getUserInfo() != null)
            throw new IllegalArgumentException("OCR endpoint must be a local HTTP service");
    }
    public Reply recognize(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("url", url)))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("OCR HTTP " + response.statusCode());
            byte[] bytes = body.readNBytes(4_000_001);
            if (bytes.length > 4_000_000) throw new IOException("OCR response too large");
            var reply = mapper.readValue(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), Reply.class);
            if (reply.model() == null || reply.model().length() > 200 || reply.lines() == null || reply.lines().size() > 3000)
                throw new IOException("Invalid OCR response");
            for (var line : reply.lines()) {
                if (line.text() == null || line.text().length() > 4000 || !Double.isFinite(line.confidence())
                        || line.confidence() < 0 || line.confidence() > 1 || line.box() == null || line.box().size() != 4
                        || line.box().stream().anyMatch(n -> n == null || !Double.isFinite(n) || n < 0)
                        || line.box().get(2) < line.box().get(0) || line.box().get(3) < line.box().get(1))
                    throw new IOException("Invalid OCR line");
            }
            return reply;
        } catch (RuntimeException ex) { throw new IOException("Invalid OCR response", ex); }
    }
}


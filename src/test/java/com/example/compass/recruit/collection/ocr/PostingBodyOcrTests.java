package com.example.compass.recruit.collection.ocr;
import com.example.compass.recruit.collection.saramin.SaraminPageParser;
import com.example.compass.recruit.collection.saramin.SaraminTestFixtures;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

public class PostingBodyOcrTests {
    public static PostingBodyOcr.Result supplement(String text, String body, String url) throws Exception { return new PostingBodyOcr(true, ignored -> reply(text)).supplement(body,url); }
    static final String URL = "https://www.saraminimage.co.kr/poster.png";
    static BodyOcrClient.Reply reply(String text) {
        return new BodyOcrClient.Reply("test-model", List.of(new BodyOcrClient.Line(text, .95, List.of(0d, 0d, 100d, 20d))));
    }
    @Test void mixedBodyKeepsImagePositionAndEscapesOcrText() throws Exception {
        var ocr = new PostingBodyOcr(true, url -> reply("<script>alert(1)</script> 지원자격"));
        var result = ocr.supplement("<div class='user_content'><p>앞 문장</p><img src='"+URL+"'><p>뒤 문장</p></div>", URL);
        var doc = org.jsoup.Jsoup.parse(result.html());
        assertThat(doc.select("script")).isEmpty();
        assertThat(doc.body().text()).contains("앞 문장 <script>alert(1)</script> 지원자격 뒤 문장");
        assertThat(result.evidence().get(0).status()).isEqualTo("SUCCESS");
        assertThat(result.evidence().get(0).lines().get(0).box()).containsExactly(0d, 0d, 100d, 20d);
    }
    @Test void imageOnlyCanPassQualityCheckButOcrNeverChangesSalaryOrDeadline() throws Exception {
        String body = "<div class='user_content'><img src='"+URL+"'></div>";
        String text = "상시 채용 연봉 9,999만원 마감 2098년 1월 1일. ".repeat(5);
        var result = new PostingBodyOcr(true, url -> reply(text)).supplement(body, URL);
        var data = SaraminPageParser.parse("123", SaraminTestFixtures.SUMMARY, body, URL, result);
        assertThat(data.description()).contains(text.trim());
        assertThat(data.deadlineTypeCode()).isEqualTo("1");
        assertThat(data.applicationEnd()).isEqualTo(java.time.Instant.parse("2099-11-30T14:59:00Z"));
        assertThat(data.summary().get("급여")).isEqualTo("면접 후 결정");
        assertThat(data.bodyOcr()).hasSize(1);
    }
    @Test void failedOcrPreservesAdequateHtmlAndRecordsFailure() throws Exception {
        var ocr = new PostingBodyOcr(true, url -> { throw new IOException("unavailable"); });
        String body = SaraminTestFixtures.BODY.replace("</div>", "<img src='"+URL+"'></div>");
        var result = ocr.supplement(body, URL);
        var data = SaraminPageParser.parse("123", SaraminTestFixtures.SUMMARY, body, URL, result);
        assertThat(data.description()).contains("지원자격");
        assertThat(data.bodyOcr().get(0).status()).isEqualTo("FAILED");
        String imageOnly="<img src='"+URL+"'>";
        assertThatThrownBy(() -> SaraminPageParser.parse("123", SaraminTestFixtures.SUMMARY, imageOnly, URL,
            ocr.supplement(imageOnly, URL))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicatesAreInferredOnceAndDisabledNeverCallsWorker() throws Exception {
        var calls = new AtomicInteger();
        var ocr = new PostingBodyOcr(true, url -> { calls.incrementAndGet(); return reply("본문"); });
        var result = ocr.supplement("<img src='"+URL+"'><img src='"+URL+"'>", URL);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.evidence()).extracting(PostingBodyOcr.Evidence::status).containsExactly("SUCCESS","DUPLICATE");
        new PostingBodyOcr(false, url -> { throw new AssertionError(); }).supplement("<img src='"+URL+"'>", URL);
    }
    @Test void arbitraryUrlsNeverReachWorkerAndImageCountIsBounded() throws Exception {
        assertThat(PostingBodyOcr.allowed("https://127.0.0.1/a")).isFalse();
        assertThat(PostingBodyOcr.allowed("https://www.saraminimage.co.kr.evil.test/a")).isFalse();
        assertThat(PostingBodyOcr.allowed("https://user@www.saraminimage.co.kr/a")).isFalse();
        assertThat(PostingBodyOcr.allowed("http://www.saraminimage.co.kr/a")).isFalse();
        var calls=new AtomicInteger();
        var ocr=new PostingBodyOcr(true,url->{calls.incrementAndGet();return reply("문구");});
        StringBuilder html=new StringBuilder("<img src='https://evil.test/a'>");
        for(int i=0;i<21;i++) html.append("<img src='").append(URL).append("?n=").append(i).append("'>");
        var result=ocr.supplement(html.toString(),URL);
        assertThat(calls.get()).isEqualTo(20);
        assertThat(result.evidence().get(0).status()).isEqualTo("UNSUPPORTED_URL");
        assertThat(result.evidence().get(21).status()).isEqualTo("LIMIT");
    }
    @Test void interruptedWorkerPropagatesCancellation() {
        var ocr=new PostingBodyOcr(true,url->{throw new InterruptedException();});
        assertThatThrownBy(()->ocr.supplement("<img src='"+URL+"'>",URL)).isInstanceOf(InterruptedException.class);
    }
    @Test void httpContractValidatesResponsesAndUsesOnlyLocalEndpoint() throws Exception {
        assertThatThrownBy(()->new BodyOcrClient("http://remote.test/ocr")).isInstanceOf(IllegalArgumentException.class);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var response=new java.util.concurrent.atomic.AtomicReference<>(
            "{\"model\":\"test\",\"lines\":[{\"text\":\"한글 본문\",\"confidence\":0.95,\"box\":[1,2,3,4]}]}");
        server.createContext("/ocr",exchange->{
            byte[] bytes=response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        try {
            var client=new BodyOcrClient("http://127.0.0.1:"+server.getAddress().getPort()+"/ocr");
            assertThat(client.recognize(URL).lines().get(0).text()).isEqualTo("한글 본문");
            response.set("{\"model\":\"test\",\"lines\":[{\"text\":\"bad\",\"confidence\":2,\"box\":[0,0,1,1]}]}");
            assertThatThrownBy(()->client.recognize(URL)).isInstanceOf(IOException.class);
            response.set("not json");
            assertThatThrownBy(()->client.recognize(URL)).isInstanceOf(IOException.class);
        } finally {server.stop(0);}
    }
}



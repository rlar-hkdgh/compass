package com.example.compass.activity.collection;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;

@Component
public class OfficialActivityClient {
    private static final int LIMIT=2_000_000;
    private long lastRequest;
    public synchronized String fetch(ActivityCollectionPlan.Target target) throws IOException,InterruptedException {
        URI uri=OfficialActivityParser.validateTarget(target);
        return fetchUri(uri);
    }
    public synchronized String fetchJobCenter(URI uri) throws IOException, InterruptedException {
        SejongJobCenterParser.validateUri(uri);
        return fetchUri(uri);
    }
    public synchronized String fetchDodream(URI uri) throws IOException, InterruptedException {
        SejongDodreamParser.validateUri(uri);
        return fetchUri(uri);
    }
    public synchronized String fetchBootcamp(String source,URI uri) throws IOException, InterruptedException {
        BootcampParser.validate(source,uri);
        return fetchUri(uri);
    }
    static int bodyLimit(URI uri) {
        return java.util.Set.of("sprint.codeit.kr","soldesk.com").contains(uri.getHost()) ? 4_000_000 : LIMIT;
    }
    private String fetchUri(URI uri) throws IOException, InterruptedException {
        return request(uri.toString());
    }
    static long requestInterval(URI uri) {
        return 3000;
    }
    String request(String url) throws IOException,InterruptedException {
        long interval=requestInterval(URI.create(url));
        long wait=interval-(System.currentTimeMillis()-lastRequest);
        if(wait>0) Thread.sleep(wait);
        lastRequest=System.currentTimeMillis();
        int limit=bodyLimit(URI.create(url));
        var response=Jsoup.connect(url).userAgent("SejongCompass/0.1 (official IT activity collection)")
            .timeout(25000).maxBodySize(limit).followRedirects(false).ignoreHttpErrors(true).execute();
        if(response.statusCode()!=200) throw new IOException("Official activity request stopped: HTTP "+response.statusCode());
        if(response.bodyAsBytes().length>=limit) throw new IOException("Official activity response too large");
        String contentType=response.contentType();
        if(contentType==null || (!contentType.startsWith("text/") && !contentType.contains("xhtml")))
            throw new IOException("Expected an HTML/text response");
        return response.body();
    }
}

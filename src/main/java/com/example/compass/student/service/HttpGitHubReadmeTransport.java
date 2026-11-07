package com.example.compass.student.service;


import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
class HttpGitHubReadmeTransport implements GitHubReadmeTransport {
    static final int MAX_RESPONSE_BYTES=2*1024*1024;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Override public Reply get(URI uri) throws IOException,InterruptedException {
        if (!"https".equals(uri.getScheme()) || !"api.github.com".equals(uri.getHost()) || uri.getPort()!=-1 || uri.getUserInfo()!=null) throw new IOException("Disallowed upstream");
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept","application/vnd.github+json").header("User-Agent","sejong-compass")
                .header("X-GitHub-Api-Version","2026-03-10").GET().build();
        // Completion occurs only after the bounded body is read, so request timeout includes body transfer.
        var response=http.send(request,info->new LimitedBody(MAX_RESPONSE_BYTES));
        return new Reply(response.statusCode(),response.headers().map(),response.body());
    }
    static final class ResponseTooLarge extends IOException {}
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit=limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription;subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            if (result.isDone()) return;
            for (var chunk:chunks) {
                int size=chunk.remaining();
                if (size>limit-buffer.size()) { subscription.cancel();result.completeExceptionally(new ResponseTooLarge());return; }
                byte[] bytes=new byte[size];chunk.get(bytes);buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(buffer.toByteArray()); }
    }
}

package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;


import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class GitHubReadmeClientTests {
    static GitHubReadmeTransport.Reply reply(int status,Map<String,List<String>> headers,byte[] body) {return new GitHubReadmeTransport.Reply(status,headers,body);}
    static byte[] success(String content) {byte[] bytes=content.getBytes(StandardCharsets.UTF_8);return JsonMapper.builder().build().writeValueAsBytes(Map.of("type","file","path","README.md","sha","a".repeat(40),"encoding","base64","size",bytes.length,"content",Base64.getEncoder().encodeToString(bytes)));}
    GitHubReadmeClient.Attempt call(int status,Map<String,List<String>> headers,byte[] body) {return new GitHubReadmeClient(uri->reply(status,headers,body)).fetch(new GitHubRepository("owner","repository"));}
    @Test void githubRepositoryNeverBuildsArbitraryHostsAndStripsGitSuffix() {
        assertThat(GitHubRepository.parse("https://github.com/owner/repository.git/").readmeApi().toString()).isEqualTo("https://api.github.com/repos/owner/repository/readme");
        for(String url:List.of("http://github.com/a/b","https://evil.test/a/b","https://github.com@evil.test/a/b","https://github.com/a/b?ref=main","https://github.com:443/a/b","https://github.com/a/%2e%2e","https://github.com/a/..","https://github.com/a/b/tree/main")) assertThatThrownBy(()->GitHubRepository.parse(url)).isInstanceOf(ApiErrors.Failure.class);
    }
    @Test void successDecodesUtf8MarkdownWithoutExecutingOrRenderingIt() {
        var result=call(200,Map.of(),success("# README\n한글 <script>untrusted()</script>"));
        assertThat(result.status()).isEqualTo("SUCCESS");assertThat(result.content()).contains("한글 <script>");assertThat(result.sha()).hasSize(40);
    }
    @Test void missingRateLimitedRedirectAndServerErrorsAreDistinct() {
        assertThat(call(404,Map.of(),new byte[0]).status()).isEqualTo("NOT_FOUND");
        assertThat(call(403,Map.of("x-ratelimit-remaining",List.of("0")),new byte[0]).status()).isEqualTo("RATE_LIMITED");
        assertThat(call(429,Map.of(),new byte[0]).status()).isEqualTo("RATE_LIMITED");
        assertThat(call(301,Map.of("Location",List.of("http://127.0.0.1")),new byte[0]).errorCode()).isEqualTo("GITHUB_REDIRECT");
        assertThat(call(503,Map.of(),new byte[0]).errorCode()).isEqualTo("GITHUB_UNAVAILABLE");
    }
    @Test void malformedOversizeTimeoutAndNetworkFailuresAreBounded() {
        assertThat(call(200,Map.of(),"{}".getBytes(StandardCharsets.UTF_8)).status()).isEqualTo("FAILED");
        assertThat(call(200,Map.of(),new byte[HttpGitHubReadmeTransport.MAX_RESPONSE_BYTES+1]).errorCode()).isEqualTo("GITHUB_RESPONSE_TOO_LARGE");
        var timed=new GitHubReadmeClient(uri->{throw new HttpTimeoutException("timeout");});
        assertThat(timed.fetch(new GitHubRepository("a","b")).errorCode()).isEqualTo("GITHUB_TIMEOUT");
        var failed=new GitHubReadmeClient(uri->{throw new IOException("unavailable");});
        assertThat(failed.fetch(new GitHubRepository("a","b")).errorCode()).isEqualTo("GITHUB_NETWORK_ERROR");
    }
    @Test void bodySubscriberCancelsBeforeKeepingOversizeContent() {
        var cancelled=new AtomicBoolean();
        var subscriber=new HttpGitHubReadmeTransport.LimitedBody(4);
        subscriber.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled.set(true);}});
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[5])));
        assertThat(cancelled.get()).isTrue();assertThat(subscriber.getBody().toCompletableFuture().isCompletedExceptionally()).isTrue();
    }
    @Test void transportRejectsNonGithubUriBeforeAnyNetworkRequest() {
        var transport=new HttpGitHubReadmeTransport();
        assertThatThrownBy(()->transport.get(java.net.URI.create("http://localhost/private"))).isInstanceOf(IOException.class);
    }
    @Test void malformedBase64Utf8AndDeclaredOversizeNeverProduceSuccess() {
        var json=JsonMapper.builder().build();
        var metadata=new HashMap<String,Object>(Map.of("type","file","path","README.md","sha","a".repeat(40),"encoding","base64","size",1,"content","not base64!"));
        assertThat(call(200,Map.of(),json.writeValueAsBytes(metadata)).status()).isEqualTo("FAILED");
        metadata.put("content",Base64.getEncoder().encodeToString(new byte[]{(byte)0xff}));
        assertThat(call(200,Map.of(),json.writeValueAsBytes(metadata)).errorCode()).isEqualTo("GITHUB_INVALID_CONTENT");
        metadata.put("size",GitHubReadmeClient.MAX_CONTENT_BYTES+1);
        assertThat(call(200,Map.of(),json.writeValueAsBytes(metadata)).errorCode()).isEqualTo("README_TOO_LARGE");
    }}


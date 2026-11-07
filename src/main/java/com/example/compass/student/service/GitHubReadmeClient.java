package com.example.compass.student.service;


import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Base64;

@Component
class GitHubReadmeClient {
    static final int MAX_CONTENT_BYTES=1024*1024;
    record Attempt(String status,String path,String sha,String content,String errorCode) {
        static Attempt failed(String status,String code) { return new Attempt(status,null,null,null,code); }
    }
    private final GitHubReadmeTransport transport;
    private final JsonMapper json=JsonMapper.builder().build();
    GitHubReadmeClient(GitHubReadmeTransport transport) { this.transport=transport; }
    Attempt fetch(GitHubRepository repository) {
        try {
            var response=transport.get(repository.readmeApi());
            int status=response.status();
            if (status==404) return Attempt.failed("NOT_FOUND","README_NOT_FOUND");
            if (status==429 || status==403 && ("0".equals(response.header("X-RateLimit-Remaining")) || !response.header("Retry-After").isEmpty())) return Attempt.failed("RATE_LIMITED","GITHUB_RATE_LIMITED");
            if (status>=300 && status<400) return Attempt.failed("FAILED","GITHUB_REDIRECT");
            if (status!=200) return Attempt.failed("FAILED",status>=500?"GITHUB_UNAVAILABLE":"GITHUB_HTTP_ERROR");
            if (response.body().length>HttpGitHubReadmeTransport.MAX_RESPONSE_BYTES) return Attempt.failed("FAILED","GITHUB_RESPONSE_TOO_LARGE");
            var node=json.readTree(response.body());
            String path=node.path("path").asText(), sha=node.path("sha").asText(), encoding=node.path("encoding").asText();
            if (node.path("size").isIntegralNumber() && node.path("size").asLong()>MAX_CONTENT_BYTES) return Attempt.failed("FAILED","README_TOO_LARGE");
            if (!node.path("content").isString() || !"file".equals(node.path("type").asText()) || !"base64".equals(encoding)
                    || path.isBlank() || path.length()>2000 || !sha.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")
                    || !node.path("size").isIntegralNumber() || node.path("size").asLong()<0 || node.path("size").asLong()>MAX_CONTENT_BYTES) return Attempt.failed("FAILED","GITHUB_INVALID_RESPONSE");
            byte[] bytes=Base64.getDecoder().decode(node.path("content").asText().replaceAll("\\s", ""));
            if (bytes.length>MAX_CONTENT_BYTES) return Attempt.failed("FAILED","README_TOO_LARGE");
            if (bytes.length!=node.path("size").asLong()) return Attempt.failed("FAILED","GITHUB_INVALID_RESPONSE");
            String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (content.indexOf('\0')>=0) return Attempt.failed("FAILED","GITHUB_INVALID_CONTENT");
            return new Attempt("SUCCESS",path,sha,content,null);
        } catch (HttpTimeoutException e) { return Attempt.failed("FAILED","GITHUB_TIMEOUT"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt();return Attempt.failed("FAILED","GITHUB_INTERRUPTED"); }
        catch (CharacterCodingException e) { return Attempt.failed("FAILED","GITHUB_INVALID_CONTENT"); }
        catch (IOException e) {
            for (Throwable cause=e;cause!=null;cause=cause.getCause()) if (cause instanceof HttpGitHubReadmeTransport.ResponseTooLarge) return Attempt.failed("FAILED","GITHUB_RESPONSE_TOO_LARGE");
            return Attempt.failed("FAILED","GITHUB_NETWORK_ERROR");
        } catch (Exception e) { return Attempt.failed("FAILED","GITHUB_INVALID_RESPONSE"); }
    }
}


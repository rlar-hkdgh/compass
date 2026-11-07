package com.example.compass.student.service;


import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

interface GitHubReadmeTransport {
    record Reply(int status, Map<String,List<String>> headers, byte[] body) {
        String header(String name) { return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).flatMap(e->e.getValue().stream()).findFirst().orElse(""); }
    }
    Reply get(URI uri) throws IOException, InterruptedException;
}

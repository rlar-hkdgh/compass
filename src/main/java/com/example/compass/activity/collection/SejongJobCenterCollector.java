package com.example.compass.activity.collection;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class SejongJobCenterCollector {
    private final OfficialActivityClient client;
    public SejongJobCenterCollector(OfficialActivityClient client) { this.client = client; }
    @FunctionalInterface interface PageReader { String read(URI uri) throws Exception; }
    public List<CollectedActivity> collect() throws Exception {
        return collect(client::fetchJobCenter);
    }
    // Package-private reader permits network-free workflow tests.
    static List<CollectedActivity> collect(PageReader reader) throws Exception {
        List<CollectedActivity> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<Set<String>> pageIdentities = new HashSet<>();
        for (int page = 1; ; page = Math.incrementExact(page)) {
            var listing = SejongJobCenterParser.listing(reader.read(SejongJobCenterParser.listUri(page)), page);
            // Include non-internship rows when detecting ignored pagination.
            if (!pageIdentities.add(listing.allIds()))
                throw new IllegalArgumentException("Recommendation pagination repeated a page");
            for (var posting : listing.postings()) {
                if (!seen.add(posting.id())) continue;
                var parsed = SejongJobCenterParser.detail(posting, reader.read(posting.uri()));
                parsed.ifPresent(results::add);
            }
            if (page >= listing.lastLinkedPage()) break;
        }
        return List.copyOf(results);
    }
}

package com.example.compass.activity.collection;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class SejongDodreamCollector {
    private final OfficialActivityClient client;
    public SejongDodreamCollector(OfficialActivityClient client) { this.client = client; }
    @FunctionalInterface interface PageReader { String read(URI uri) throws Exception; }
    public List<CollectedActivity> collect() throws Exception { return collect(client::fetchDodream); }
    static List<CollectedActivity> collect(PageReader reader) throws Exception {
        List<CollectedActivity> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<Set<String>> pages = new HashSet<>();
        for (int page = 1; ; page = Math.incrementExact(page)) {
            var listing = SejongDodreamParser.listing(reader.read(SejongDodreamParser.listUri(page)), page);
            if (!pages.add(listing.allIds())) throw new IllegalArgumentException("Dodream pagination repeated a page");
            for (var program : listing.programs()) {
                if (!seen.add(program.id())) continue;
                SejongDodreamParser.detail(program, reader.read(program.uri())).ifPresent(result::add);
            }
            if (!listing.hasNext()) break;
        }
        return List.copyOf(result);
    }
}

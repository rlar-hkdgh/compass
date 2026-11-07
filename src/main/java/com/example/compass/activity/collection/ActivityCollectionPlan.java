package com.example.compass.activity.collection;

import java.util.List;
import java.util.Map;

/** Explicitly reviewed targets only: this is not an unrestricted site crawler. */
public record ActivityCollectionPlan(List<Target> targets) {
    public record Target(String type, String source, String url, String contentSelector,
                         String titleSelector, String evidenceSelector, String provider,
                         Map<String,String> detail) {}
}

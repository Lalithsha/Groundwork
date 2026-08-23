package com.groundwork.evidence.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/capabilities")
public class ProductCapabilitiesController {
    private final Map<String, Boolean> features;

    public ProductCapabilitiesController(
            @Value("${groundwork.features.evidence-platform:true}") boolean evidence,
            @Value("${groundwork.features.github:true}") boolean github,
            @Value("${groundwork.features.atlassian:true}") boolean atlassian,
            @Value("${groundwork.features.grounded-ai:true}") boolean groundedAi,
            @Value("${groundwork.features.release-records:true}") boolean releases) {
        this.features = Map.of("evidencePlatform", evidence, "github", github, "atlassian", atlassian,
            "groundedAi", groundedAi, "releaseRecords", releases);
    }

    @GetMapping
    public Map<String, Object> capabilities() {
        return Map.of("product", "Groundwork", "contractVersion", "2026-08-24", "features", features);
    }
}

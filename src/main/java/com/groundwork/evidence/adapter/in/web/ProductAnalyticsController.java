package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.ProductAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/analytics")
public class ProductAnalyticsController {
    private final ProductAnalyticsService analytics;
    private final WorkspaceAccessService access;

    public ProductAnalyticsController(ProductAnalyticsService analytics, WorkspaceAccessService access) {
        this.analytics = analytics;
        this.access = access;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@PathVariable UUID workspaceId) {
        access.requireViewer(workspaceId);
        return analytics.summary(workspaceId);
    }
}

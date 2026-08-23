package com.groundwork.evidence.adapter.in.web;

import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.evidence.application.AtlassianOAuthService;
import com.groundwork.evidence.application.CurrentUserIdResolver;
import com.groundwork.evidence.domain.ConnectorConnection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AtlassianOAuthController {
    private final AtlassianOAuthService oauth;
    private final WorkspaceAccessService access;
    private final CurrentUserIdResolver users;
    private final String publicBaseUrl;

    public AtlassianOAuthController(AtlassianOAuthService oauth, WorkspaceAccessService access,
            CurrentUserIdResolver users, @Value("${groundwork.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.oauth = oauth;
        this.access = access;
        this.users = users;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    @PostMapping("/workspaces/{workspaceId}/connections/atlassian/authorize")
    public AtlassianOAuthService.AuthorizationStart authorize(@PathVariable UUID workspaceId,
            @Valid @RequestBody AuthorizationRequest request) {
        access.requireAdmin(workspaceId);
        return oauth.start(workspaceId, users.optional().orElse(null), request.provider(),
            request.scopes(), request.selectedResources() == null ? Map.of() : request.selectedResources());
    }

    @GetMapping("/integrations/atlassian/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        ConnectorConnection connection = oauth.callback(code, state);
        String location = publicBaseUrl + "/connections?connected=" +
            URLEncoder.encode(connection.provider(), StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    public record AuthorizationRequest(@NotBlank String provider, List<String> scopes,
                                       Map<String, Object> selectedResources) {}
}

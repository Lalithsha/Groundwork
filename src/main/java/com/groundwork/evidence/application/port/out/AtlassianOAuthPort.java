package com.groundwork.evidence.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface AtlassianOAuthPort {
    JsonNode exchangeAuthorizationCode(String code, String redirectUri);
    Map<String, Object> tokenBundle(JsonNode response, String priorRefreshToken);
    JsonNode accessibleResources(String accessToken);
}

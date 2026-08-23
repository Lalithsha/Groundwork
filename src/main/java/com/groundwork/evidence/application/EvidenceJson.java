package com.groundwork.evidence.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EvidenceJson {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public EvidenceJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Value cannot be encoded as JSON", exception);
        }
    }

    public Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return mapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON object is invalid", exception);
        }
    }

    public List<Map<String, Object>> list(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return mapper.readValue(value, LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON array is invalid", exception);
        }
    }

    public JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload is not valid JSON", exception);
        }
    }
}

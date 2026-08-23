package com.groundwork.evidence.application;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OpenApiDiffAnalyzer {
    private static final Set<String> HTTP_METHODS = Set.of(
        "get", "put", "post", "delete", "options", "head", "patch", "trace");

    public DiffResult compare(String baseContent, String headContent) {
        Map<String, Object> base = parse(baseContent);
        Map<String, Object> head = parse(headContent);
        Map<String, Set<String>> baseOperations = operations(base);
        Map<String, Set<String>> headOperations = operations(head);
        List<String> breaking = new ArrayList<>();
        List<String> additions = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : baseOperations.entrySet()) {
            if (!headOperations.containsKey(entry.getKey())) {
                breaking.add("Removed path " + entry.getKey());
                continue;
            }
            for (String method : entry.getValue()) {
                if (!headOperations.get(entry.getKey()).contains(method)) {
                    breaking.add("Removed operation " + method.toUpperCase(Locale.ROOT) + " " + entry.getKey());
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : headOperations.entrySet()) {
            if (!baseOperations.containsKey(entry.getKey())) {
                additions.add("Added path " + entry.getKey());
                continue;
            }
            for (String method : entry.getValue()) {
                if (!baseOperations.get(entry.getKey()).contains(method)) {
                    additions.add("Added operation " + method.toUpperCase(Locale.ROOT) + " " + entry.getKey());
                }
            }
        }
        return new DiffResult(List.copyOf(breaking), List.copyOf(additions),
            baseOperations.size(), headOperations.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String content) {
        if (content == null || content.isBlank()) return Map.of();
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(2_000_000);
        options.setMaxAliasesForCollections(10);
        Object value = new Yaml(new SafeConstructor(options)).load(content);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("OpenAPI content must be an object");
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
        return normalized;
    }

    private Map<String, Set<String>> operations(Map<String, Object> document) {
        Object pathsValue = document.get("paths");
        if (!(pathsValue instanceof Map<?, ?> paths)) return Map.of();
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        paths.forEach((path, value) -> {
            Set<String> methods = new LinkedHashSet<>();
            if (value instanceof Map<?, ?> pathItem) {
                pathItem.keySet().stream().map(String::valueOf).map(item -> item.toLowerCase(Locale.ROOT))
                    .filter(HTTP_METHODS::contains).forEach(methods::add);
            }
            operations.put(String.valueOf(path), Set.copyOf(methods));
        });
        return Map.copyOf(operations);
    }

    public record DiffResult(List<String> breakingChanges, List<String> additions,
                             int basePathCount, int headPathCount) {
        public boolean breaking() { return !breakingChanges.isEmpty(); }
        public boolean changed() { return breaking() || !additions.isEmpty(); }
    }
}

package com.groundwork.evidence.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CodeOwnersMatcher {
    public Map<String, List<String>> ownersFor(List<String> paths, String content) {
        if (content == null || content.isBlank() || paths.isEmpty()) return Map.of();
        List<Rule> rules = parse(content);
        Map<String, List<String>> matches = new LinkedHashMap<>();
        for (String path : paths) {
            List<String> owners = List.of();
            for (Rule rule : rules) {
                if (rule.pattern().matcher(path).matches()) owners = rule.owners();
            }
            if (!owners.isEmpty()) matches.put(path, owners);
        }
        return Map.copyOf(matches);
    }

    private List<Rule> parse(String content) {
        List<Rule> rules = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            List<String> owners = new ArrayList<>();
            for (int index = 1; index < parts.length; index++) {
                if (parts[index].startsWith("@")) owners.add(parts[index]);
            }
            if (!owners.isEmpty()) rules.add(new Rule(glob(parts[0]), List.copyOf(owners)));
        }
        return rules;
    }

    private Pattern glob(String value) {
        boolean rootAnchored = value.startsWith("/");
        String pattern = rootAnchored ? value.substring(1) : value;
        boolean anyDirectory = !rootAnchored && !pattern.contains("/");
        if (pattern.endsWith("/")) pattern += "**";
        StringBuilder regex = new StringBuilder("^");
        if (anyDirectory) regex.append("(?:.*/)?");
        for (int index = 0; index < pattern.length(); index++) {
            char current = pattern.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < pattern.length() && pattern.charAt(index + 1) == '*';
                if (doubleStar) { regex.append(".*"); index++; }
                else regex.append("[^/]*");
            } else if (current == '?') regex.append("[^/]");
            else {
                if (".[]{}()+-^$|\\".indexOf(current) >= 0) regex.append('\\');
                regex.append(current);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private record Rule(Pattern pattern, List<String> owners) {}
}

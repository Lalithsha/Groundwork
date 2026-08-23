package com.groundwork.application;

import com.groundwork.domain.model.TextChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class TokenAwareTextChunker implements TextChunker {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+");
    private static final Pattern PAGE_PATTERN = Pattern.compile("(?i)^page\\s+(\\d+)(?:\\s+of\\s+\\d+)?$");

    private final int targetTokens;
    private final int overlapTokens;

    public TokenAwareTextChunker(
            @Value("${groundwork.ingestion.chunk-target-tokens:500}") int targetTokens,
            @Value("${groundwork.ingestion.chunk-overlap-tokens:60}") int overlapTokens) {
        if (targetTokens < 100) {
            throw new IllegalArgumentException("Chunk target must be at least 100 tokens");
        }
        if (overlapTokens < 0 || overlapTokens >= targetTokens) {
            throw new IllegalArgumentException("Chunk overlap must be between zero and the target size");
        }
        this.targetTokens = targetTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Section> sections = splitSections(text);
        List<TextChunk> chunks = new ArrayList<>();
        List<String> pendingTokens = new ArrayList<>();
        String sectionTitle = null;
        Integer pageNumber = null;

        for (Section section : sections) {
            if (section.heading() != null) {
                sectionTitle = section.heading();
            }
            if (section.pageNumber() != null) {
                pageNumber = section.pageNumber();
            }
            pendingTokens.addAll(tokenize(section.content()));
            while (pendingTokens.size() >= targetTokens) {
                chunks.add(toChunk(chunks.size(), pendingTokens.subList(0, targetTokens), sectionTitle, pageNumber));
                pendingTokens = new ArrayList<>(pendingTokens.subList(targetTokens - overlapTokens, pendingTokens.size()));
            }
        }

        if (!pendingTokens.isEmpty()) {
            chunks.add(toChunk(chunks.size(), pendingTokens, sectionTitle, pageNumber));
        }
        return List.copyOf(chunks);
    }

    private List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = null;
        Integer currentPage = null;
        StringBuilder content = new StringBuilder();

        for (String line : text.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.trim();
            Matcher pageMatcher = PAGE_PATTERN.matcher(trimmed);
            boolean heading = trimmed.matches("^#{1,6}\\s+.+") ||
                (trimmed.length() < 100 && trimmed.matches("^[A-Z][A-Za-z0-9 /&:_-]+:$"));

            if (heading || pageMatcher.matches()) {
                if (!content.isEmpty()) {
                    sections.add(new Section(currentHeading, currentPage, content.toString()));
                    content.setLength(0);
                }
                if (heading) {
                    currentHeading = trimmed.replaceFirst("^#{1,6}\\s+", "").replaceFirst(":$", "");
                } else {
                    currentPage = Integer.valueOf(pageMatcher.group(1));
                }
                continue;
            }

            if (!trimmed.isBlank()) {
                if (!content.isEmpty()) content.append('\n');
                content.append(trimmed);
            }
        }
        if (!content.isEmpty()) {
            sections.add(new Section(currentHeading, currentPage, content.toString()));
        }
        return sections;
    }

    private List<String> tokenize(String content) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private TextChunk toChunk(int index, List<String> tokens, String heading, Integer page) {
        return new TextChunk(index, String.join(" ", tokens), tokens.size(), heading, page);
    }

    private record Section(String heading, Integer pageNumber, String content) {}
}

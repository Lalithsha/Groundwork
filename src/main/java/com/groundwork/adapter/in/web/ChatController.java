package com.groundwork.adapter.in.web;

import com.groundwork.application.ChatAnswerService;
import com.groundwork.application.ChatAnswerService.ChatQuery;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.domain.model.ChatResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final int MAX_QUESTION_LENGTH = 2_000;

    private final ChatAnswerService chat;
    private final Executor streamingExecutor;
    private final WorkspaceAccessService access;

    public ChatController(ChatAnswerService chat, @Qualifier("chatStreamingExecutor") Executor streamingExecutor,
            WorkspaceAccessService access) {
        this.chat = chat;
        this.streamingExecutor = streamingExecutor;
        this.access = access;
    }

    public record ChatRequest(String question, String retrievalMode, String documentFilter, UUID workspaceId) {}

    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequest request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        String validationError = validateQuestion(request.question());
        String mode = normalizeMode(request.retrievalMode());
        if (validationError != null) {
            return ResponseEntity.badRequest().body(new ChatResponseDto(validationError, java.util.List.of(), mode));
        }
        String resolvedRequestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        access.requireViewer(request.workspaceId());
        Mention mention = parseMention(request.question(), request.documentFilter());
        return ResponseEntity.ok(chat.answer(new ChatQuery(mention.question(), mode, request.workspaceId(),
            mention.documentFilter(), resolvedRequestId)));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String question,
            @RequestParam(defaultValue = "hybrid_rerank") String mode,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) String documentFilter) {
        SseEmitter emitter = new SseEmitter(90_000L);
        String validationError = validateQuestion(question);
        if (validationError != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", validationError)));
                emitter.complete();
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
            return emitter;
        }

        access.requireViewer(workspaceId);

        streamingExecutor.execute(() -> stream(question, normalizeMode(mode), workspaceId, documentFilter, emitter));
        return emitter;
    }

    private void stream(String question, String mode, UUID workspaceId, String documentFilter, SseEmitter emitter) {
        String requestId = UUID.randomUUID().toString();
        try {
            emitter.send(SseEmitter.event().name("retrieval_started").data(Map.of("requestId", requestId)));
            Mention mention = parseMention(question, documentFilter);
            var prepared = chat.prepare(new ChatQuery(mention.question(), mode, workspaceId,
                mention.documentFilter(), requestId));
            emitter.send(SseEmitter.event().name("sources").data(prepared.citations()));

            if (prepared.chunks().isEmpty() || !chat.generationPort().isAvailable()) {
                emitFallback(chat.fallback(question, prepared.chunks()), emitter);
            } else {
                chat.generationPort().stream(prepared.prompt(), token -> sendToken(emitter, token));
            }
            emitter.send(SseEmitter.event().name("completed").data(Map.of(
                "requestId", requestId,
                "evidenceStatus", prepared.chunks().isEmpty() ? "INSUFFICIENT" : "GROUNDED")));
            emitter.complete();
        } catch (Exception exception) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                    "requestId", requestId, "message", "Chat stream could not be completed")));
            } catch (IOException ignored) {
                // The client has already disconnected.
            }
            emitter.completeWithError(exception);
        }
    }

    private void emitFallback(String answer, SseEmitter emitter) {
        for (String token : answer.split("(?<=\\s)")) sendToken(emitter, token);
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException exception) {
            throw new StreamDisconnectedException(exception);
        }
    }

    private String validateQuestion(String question) {
        if (question == null || question.isBlank()) return "Question is required.";
        if (question.length() > MAX_QUESTION_LENGTH) {
            return "Question exceeds the maximum length of " + MAX_QUESTION_LENGTH + " characters.";
        }
        return null;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "hybrid_rerank";
        return switch (mode.toLowerCase()) {
            case "naive", "vector", "hybrid", "hybrid_rerank" -> mode.toLowerCase();
            default -> "hybrid_rerank";
        };
    }

    private Mention parseMention(String question, String explicitFilter) {
        if (explicitFilter != null && !explicitFilter.isBlank()) {
            String filter = explicitFilter.strip();
            String marker = "@" + filter;
            String cleanedQuestion = question.startsWith(marker) ? question.substring(marker.length()).strip() : question;
            return new Mention(cleanedQuestion, filter);
        }
        if (!question.startsWith("@")) return new Mention(question, null);
        int separator = question.indexOf(' ');
        if (separator < 0) return new Mention(question, null);
        return new Mention(question.substring(separator + 1).strip(), question.substring(1, separator).strip());
    }

    private record Mention(String question, String documentFilter) {}
    private static final class StreamDisconnectedException extends RuntimeException {
        private StreamDisconnectedException(Throwable cause) { super(cause); }
    }
}

package com.groundwork.adapter.in.web;

import com.groundwork.application.ChatAnswerService;
import com.groundwork.application.WorkspaceAccessService;
import com.groundwork.domain.model.ChatResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {
    private ChatAnswerService chat;
    private WorkspaceAccessService access;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        chat = mock(ChatAnswerService.class);
        access = mock(WorkspaceAccessService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(chat, Runnable::run, access)).build();
    }

    @Test
    void answersValidQuestion() throws Exception {
        when(chat.answer(any())).thenReturn(new ChatResponseDto(
            "Retries are exhausted after five attempts [C1].", List.of(), "hybrid_rerank",
            List.of(), "GROUNDED", "request-1"));

        mvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What happens after retries?\",\"retrievalMode\":\"hybrid_rerank\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Retries are exhausted after five attempts [C1]."))
            .andExpect(jsonPath("$.citations").isArray())
            .andExpect(jsonPath("$.evidenceStatus").value("GROUNDED"));

        verify(access).requireViewer(null);
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.answer").value("Question is required."));
    }

    @Test
    void rejectsOversizedQuestion() throws Exception {
        String question = "x".repeat(2001);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + question + "\"}"))
            .andExpect(status().isBadRequest());
    }
}

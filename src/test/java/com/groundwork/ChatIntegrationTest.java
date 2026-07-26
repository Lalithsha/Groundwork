package com.groundwork;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testChatEndpointSuccess() throws Exception {
        String requestJson = "{\"question\":\"What happens when a delivery exhausts all retry attempts?\",\"retrievalMode\":\"hybrid_rerank\"}";

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.retrievedContexts").isArray());
    }
}

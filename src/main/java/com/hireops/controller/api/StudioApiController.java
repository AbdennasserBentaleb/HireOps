package com.hireops.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/studio")
@CrossOrigin(origins = "http://localhost:3000")
public class StudioApiController {

    private static final Logger log = LoggerFactory.getLogger(StudioApiController.class);
    private final ChatClient chatClient;

    public StudioApiController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> testPrompt(@RequestBody Map<String, String> payload) {
        String systemMsg = payload.getOrDefault("system", "You are a helpful AI.");
        String userMsg = payload.getOrDefault("user", "Hello");

        try {
            String response = chatClient.prompt()
                    .system(systemMsg)
                    .user(userMsg)
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            log.error("Failed to generate response: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

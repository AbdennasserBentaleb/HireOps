package com.hireops.controller.api;

import com.hireops.model.UserProfile;
import com.hireops.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;

/**
 * Provides real-time AI infrastructure status — Ollama health, loaded models,
 * and a live model test endpoint. Powers the Settings page AI Status Panel.
 */
@RestController
@RequestMapping("/api/ai")
public class AiStatusController {

    private static final Logger log = LoggerFactory.getLogger(AiStatusController.class);

    @Autowired(required = false)
    private OllamaApi ollamaApi;

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    private final UserProfileRepository userProfileRepository;

    @Value("${spring.ai.ollama.base-url:http://ollama:11434}")
    private String ollamaBaseUrl;

    public AiStatusController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * Returns the full AI infrastructure status:
     * - Connection health (HEALTHY / DOWN / CONFIG_ERROR)
     * - List of models loaded in Ollama with sizes
     * - Currently configured model from user profile
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        Map<String, Object> response = new LinkedHashMap<>();

        if (ollamaApi == null) {
            response.put("status", "CONFIG_ERROR");
            response.put("message", "OllamaApi bean is missing. Check spring.ai.ollama.base-url config.");
            response.put("ollamaUrl", ollamaBaseUrl);
            return ResponseEntity.ok(response);
        }

        try {
            OllamaApi.ListModelResponse modelsResponse = ollamaApi.listModels();
            List<Map<String, Object>> models = new ArrayList<>();

            if (modelsResponse != null && modelsResponse.models() != null) {
                for (OllamaApi.Model m : modelsResponse.models()) {
                    Map<String, Object> modelInfo = new LinkedHashMap<>();
                    modelInfo.put("name", m.name());
                    // Size in bytes → human readable GB/MB
                    modelInfo.put("size", formatSize(m.size()));
                    modelInfo.put("sizeBytes", m.size());
                    modelInfo.put("modifiedAt", m.modifiedAt() != null ? m.modifiedAt().toString() : null);
                    models.add(modelInfo);
                }
            }

            // Get the configured model from user profile
            String configuredModel = "llama3";
            try {
                List<UserProfile> profiles = userProfileRepository.findAll();
                if (!profiles.isEmpty() && profiles.get(0).getAiModel() != null) {
                    configuredModel = profiles.get(0).getAiModel();
                }
            } catch (Exception e) {
                log.warn("Could not read user profile for configured model: {}", e.getMessage());
            }

            // Check if configured model is actually loaded
            String finalConfiguredModel = configuredModel;
            boolean configuredModelLoaded = models.stream()
                    .anyMatch(m -> m.get("name") != null &&
                            ((String) m.get("name")).startsWith(finalConfiguredModel));

            response.put("status", "HEALTHY");
            response.put("ollamaUrl", ollamaBaseUrl);
            response.put("models", models);
            response.put("modelCount", models.size());
            response.put("configuredModel", configuredModel);
            response.put("configuredModelLoaded", configuredModelLoaded);

            if (!configuredModelLoaded && !models.isEmpty()) {
                response.put("warning", "Configured model '" + configuredModel +
                        "' is not loaded. Available: " + models.stream()
                                .map(m -> (String) m.get("name")).toList());
            } else if (models.isEmpty()) {
                response.put("warning", "No models are loaded. The engine will automatically pull '" + configuredModel + "' on the first job evaluation.");
            }

        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("ollamaUrl", ollamaBaseUrl);
            response.put("error", e.getMessage());
            response.put("hint", "Check that the Ollama container is running: docker compose ps");
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Tests a specific model by sending a one-word prompt.
     * Returns the generated text so the user can confirm the model works end-to-end.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testModel(
            @RequestParam(defaultValue = "llama3") String model) {

        Map<String, Object> response = new LinkedHashMap<>();

        if (chatClientBuilder == null) {
            response.put("success", false);
            response.put("error", "ChatClient not available. OllamaAI may not be configured.");
            return ResponseEntity.ok(response);
        }

        try {
            long start = System.currentTimeMillis();
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(
                            org.springframework.ai.ollama.api.OllamaOptions.builder().model(model).build())
                    .build();

            String testResult = chatClient.prompt()
                    .user("Reply with exactly one word: READY")
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - start;

            response.put("success", true);
            response.put("model", model);
            response.put("response", testResult != null ? testResult.trim() : "(empty)");
            response.put("responseTimeMs", elapsed);

        } catch (Exception e) {
            boolean isMissingModel = e.getMessage() != null && 
                (e.getMessage().contains("model") && (e.getMessage().contains("not found") || e.getMessage().contains("404")));
            
            if (isMissingModel) {
                final String finalModel = model;
                new Thread(() -> {
                    try {
                        log.info("Starting background pull for missing model: {}", finalModel);
                        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                        String pullJson = "{\"name\": \"" + finalModel + "\"}";
                        java.net.http.HttpRequest pullReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(ollamaBaseUrl + "/api/pull"))
                                .header("Content-Type", "application/json")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(pullJson))
                                .build();
                        client.send(pullReq, java.net.http.HttpResponse.BodyHandlers.discarding());
                        log.info("Background pull request sent for model: {}", finalModel);
                    } catch (Exception ex) {
                        log.error("Background pull failed for {}: {}", finalModel, ex.getMessage());
                    }
                }).start();
                
                response.put("success", false);
                response.put("model", model);
                response.put("error", "Model '" + model + "' is not pulled yet.");
                response.put("hint", "Auto-pull triggered in the background. Please wait 2-3 minutes and refresh/test again.");
            } else {
                response.put("success", false);
                response.put("model", model);
                response.put("error", e.getMessage());
                response.put("hint", "Model '" + model + "' may have encountered an error. Check application logs.");
            }
        }

        return ResponseEntity.ok(response);
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "Unknown";
        if (bytes >= 1_000_000_000L) return String.format("%.1f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L) return String.format("%.0f MB", bytes / 1_000_000.0);
        return bytes + " B";
    }
}

package com.hireops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcasts AI processing progress events to all connected SSE clients.
 * Thread-safe — OllamaMatchmakerService runs on async threads.
 */
@Service
public class AiProgressService {

    private static final Logger log = LoggerFactory.getLogger(AiProgressService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        log.debug("SSE client connected. Active emitters: {}", emitters.size());
        return emitter;
    }

    /**
     * Broadcasts a progress event to all connected dashboards.
     * @param type  "info" | "success" | "error" | "start"
     * @param message  Human-readable message shown in the Activity Log
     */
    public void broadcast(String type, String message) {
        String timestamp = LocalTime.now().format(TIME_FMT);
        String payload = "{\"type\":\"" + type + "\",\"message\":\"" + escapeJson(message) + "\",\"time\":\"" + timestamp + "\"}";

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}

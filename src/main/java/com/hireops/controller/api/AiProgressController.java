package com.hireops.controller.api;

import com.hireops.service.AiProgressService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams real-time AI scoring progress to connected dashboard clients.
 * Connect via: new EventSource('/api/ai/progress/stream')
 */
@RestController
@RequestMapping("/api/ai/progress")
public class AiProgressController {

    private final AiProgressService aiProgressService;

    public AiProgressController(AiProgressService aiProgressService) {
        this.aiProgressService = aiProgressService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return aiProgressService.register();
    }
}

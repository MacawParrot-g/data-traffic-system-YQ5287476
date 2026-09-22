package org.example.controller;

import org.example.config.ShutdownNotifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final ShutdownNotifier shutdownNotifier;

    public SseController(ShutdownNotifier shutdownNotifier) {
        this.shutdownNotifier = shutdownNotifier;
    }

    @GetMapping(value = "/connect", produces = "text/event-stream")
    public SseEmitter connect() {
        return shutdownNotifier.createEmitter();
    }
}

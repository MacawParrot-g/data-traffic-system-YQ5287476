package org.example.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ShutdownNotifier {

    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        globalEmitters.add(emitter);
        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));
        return emitter;
    }

    public SseEmitter createEmitterForUser(String userName) {
        SseEmitter emitter = new SseEmitter(0L);
        userEmitters.computeIfAbsent(userName, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userName, emitter));
        emitter.onTimeout(() -> removeEmitter(userName, emitter));
        emitter.onError(e -> removeEmitter(userName, emitter));
        return emitter;
    }

    public void sendToUser(String userName, String event, String data) {
        List<SseEmitter> emitters = userEmitters.get(userName);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(userName, emitter);
            }
        }
    }

    public void notifyAll(String event, String data) {
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                globalEmitters.remove(emitter);
            }
        }
        for (Map.Entry<String, List<SseEmitter>> entry : userEmitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                    emitter.complete();
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    private void removeEmitter(String userName, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userName);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userName);
            }
        }
    }
}

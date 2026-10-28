package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.DeepSeekService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deepseek")
public class DeepSeekController {

    @Autowired
    private DeepSeekService deepSeekService;

    @PostMapping("/chat")
    @LogExecutionTime("DeepSeek-对话")
    @SuppressWarnings("unchecked")
    public Result chat(@RequestBody Map<String, Object> body) {
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        String model = (String) body.get("model");
        Double temperature = body.get("temperature") != null
                ? ((Number) body.get("temperature")).doubleValue() : null;
        Integer maxTokens = body.get("maxTokens") != null
                ? ((Number) body.get("maxTokens")).intValue() : null;
        return deepSeekService.chat(messages, model, temperature, maxTokens);
    }

    @GetMapping("/status")
    @LogExecutionTime("DeepSeek-配置状态")
    public Result status() {
        return deepSeekService.status();
    }

    @GetMapping("/sessions")
    @LogExecutionTime("DeepSeek-会话列表")
    public Result listSessions() {
        return deepSeekService.listSessions();
    }

    @PostMapping("/session")
    @LogExecutionTime("DeepSeek-新建会话")
    public Result createSession() {
        return deepSeekService.createSession();
    }

    @GetMapping("/session")
    @LogExecutionTime("DeepSeek-获取会话")
    public Result getSession(@RequestParam String sessionId) {
        return deepSeekService.getSession(sessionId);
    }

    @PostMapping("/session/save")
    @LogExecutionTime("DeepSeek-保存会话")
    @SuppressWarnings("unchecked")
    public Result saveSession(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        Integer totalTokens = body.get("totalTokens") != null
                ? ((Number) body.get("totalTokens")).intValue() : null;
        return deepSeekService.saveSession(sessionId, messages, totalTokens);
    }

    @DeleteMapping("/session")
    @LogExecutionTime("DeepSeek-删除会话")
    public Result deleteSession(@RequestParam String sessionId) {
        return deepSeekService.deleteSession(sessionId);
    }
}
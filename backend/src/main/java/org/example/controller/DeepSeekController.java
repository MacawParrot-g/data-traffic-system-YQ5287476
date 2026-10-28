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
}
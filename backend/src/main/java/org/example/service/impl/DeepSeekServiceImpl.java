// 文件路径: backend/src/main/java/org/example/service/impl/DeepSeekServiceImpl.java
package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.service.DeepSeekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekServiceImpl.class);

    @Qualifier("deepSeekRestTemplate")
    @Autowired
    private RestTemplate deepSeekRestTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${deepseek.api-key:}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String defaultModel;

    @Override
    @SuppressWarnings("unchecked")
    public Result chat(List<Map<String, String>> messages, String model, Double temperature, Integer maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail("DeepSeek API Key 未配置，请在服务器 .env 文件中设置 DEEPSEEK_API_KEY");
        }
        if (messages == null || messages.isEmpty()) {
            return Result.fail("对话内容不能为空");
        }

        String url = baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", (model == null || model.isBlank()) ? defaultModel : model);
        body.put("messages", messages);
        body.put("stream", false);
        if (temperature != null) body.put("temperature", temperature);
        if (maxTokens != null) body.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> resp = deepSeekRestTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> json = resp.getBody();
            if (json == null) {
                return Result.fail("DeepSeek 返回空响应");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
            if (choices == null || choices.isEmpty()) {
                return Result.fail("DeepSeek 未返回有效结果");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message != null ? (String) message.get("content") : null;
            String reasoning = message != null ? (String) message.get("reasoning_content") : null;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", content);
            data.put("reasoningContent", reasoning);
            data.put("model", json.get("model"));
            data.put("finishReason", choices.get(0).get("finish_reason"));
            data.put("usage", json.get("usage"));
            return Result.success("对话成功", data);

        } catch (HttpStatusCodeException e) {
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            log.error("DeepSeek 调用失败, status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return Result.fail("DeepSeek 调用失败(" + e.getStatusCode().value() + ")：" + detail);
        } catch (Exception e) {
            log.error("DeepSeek 调用异常: {}", e.getMessage());
            return Result.fail("DeepSeek 服务调用异常：" + e.getMessage());
        }
    }

    @Override
    public Result status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", apiKey != null && !apiKey.isBlank());
        data.put("model", defaultModel);
        data.put("baseUrl", baseUrl);
        return Result.success("获取配置成功", data);
    }

    /** 从 DeepSeek/OpenAI 错误响应体中解析可读错误信息。 */
    @SuppressWarnings("unchecked")
    private String extractErrorMessage(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return "未知错误";
        try {
            Map<String, Object> map = objectMapper.readValue(rawBody, Map.class);
            Object error = map.get("error");
            if (error instanceof Map) {
                Object msg = ((Map<String, Object>) error).get("message");
                if (msg != null) return String.valueOf(msg);
            }
        } catch (Exception ignore) {
            // 解析失败则返回原文
        }
        return rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody;
    }
}
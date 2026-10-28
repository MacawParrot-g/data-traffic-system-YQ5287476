package org.example.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.common.UserContext;
import org.example.service.DeepSeekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekServiceImpl.class);

    private static final String IDX_PREFIX = "deepseek:idx:";
    private static final String CONV_PREFIX = "deepseek:conv:";
    private static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final int MAX_SESSIONS = 50;

    @Qualifier("deepSeekRestTemplate")
    @Autowired
    private RestTemplate deepSeekRestTemplate;

    @Qualifier("deepSeekRedisTemplate")
    @Autowired
    private RedisTemplate<String, Object> deepSeekRedisTemplate;

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

    // ==================== 会话持久化 ====================

    @Override
    public Result listSessions() {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        List<Map<String, Object>> metas = readIndex(username);
        metas.sort((a, b) -> Long.compare(asLong(b.get("updatedAt")), asLong(a.get("updatedAt"))));
        return Result.success("获取会话列表成功", metas);
    }

    @Override
    public Result createSession() {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        String sessionId = "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long now = System.currentTimeMillis();

        try {
            String ck = convKey(username, sessionId);
            deepSeekRedisTemplate.opsForValue().set(ck, objectMapper.writeValueAsString(new ArrayList<>()));
            deepSeekRedisTemplate.expire(ck, SESSION_TTL);
        } catch (Exception e) {
            return Result.fail("创建会话失败：" + e.getMessage());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", sessionId);
        meta.put("title", "新对话");
        meta.put("createdAt", now);
        meta.put("updatedAt", now);
        meta.put("totalTokens", 0);
        meta.put("messageCount", 0);

        List<Map<String, Object>> metas = readIndex(username);
        metas.add(0, meta);
        writeIndex(username, metas);
        return Result.success("创建会话成功", meta);
    }

    @Override
    public Result getSession(String sessionId) {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        if (sessionId == null || sessionId.isBlank()) return Result.fail("会话ID不能为空");
        List<Map<String, Object>> messages = readConv(username, sessionId);
        return Result.success("获取会话成功", messages);
    }

    @Override
    public Result saveSession(String sessionId, List<Map<String, Object>> messages, Integer totalTokens) {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        if (sessionId == null || sessionId.isBlank()) return Result.fail("会话ID不能为空");
        if (messages == null) messages = new ArrayList<>();

        List<Map<String, Object>> clean = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (Boolean.TRUE.equals(m.get("pending"))) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("role", m.get("role"));
            c.put("content", m.get("content"));
            if (m.get("reasoning") != null) c.put("reasoning", m.get("reasoning"));
            if (m.get("usage") != null) c.put("usage", m.get("usage"));
            if (m.get("error") != null) c.put("error", m.get("error"));
            clean.add(c);
        }

        try {
            String ck = convKey(username, sessionId);
            deepSeekRedisTemplate.opsForValue().set(ck, objectMapper.writeValueAsString(clean));
            deepSeekRedisTemplate.expire(ck, SESSION_TTL);
        } catch (Exception e) {
            return Result.fail("保存会话失败：" + e.getMessage());
        }

        List<Map<String, Object>> metas = readIndex(username);
        Map<String, Object> target = null;
        for (Map<String, Object> m : metas) {
            if (sessionId.equals(m.get("id"))) { target = m; break; }
        }
        long now = System.currentTimeMillis();
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("id", sessionId);
            target.put("createdAt", now);
            metas.add(0, target);
        }
        target.put("updatedAt", now);
        target.put("totalTokens", totalTokens == null ? 0 : totalTokens);
        target.put("messageCount", clean.size());
        target.put("title", deriveTitle(clean));
        writeIndex(username, metas);
        return Result.success("保存成功", target);
    }

    @Override
    public Result deleteSession(String sessionId) {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        if (sessionId == null || sessionId.isBlank()) return Result.fail("会话ID不能为空");
        try {
            deepSeekRedisTemplate.delete(convKey(username, sessionId));
        } catch (Exception e) {
            log.warn("删除会话内容失败: {}", e.getMessage());
        }
        List<Map<String, Object>> metas = readIndex(username);
        metas.removeIf(m -> sessionId.equals(m.get("id")));
        writeIndex(username, metas);
        return Result.success("删除会话成功");
    }

    // ==================== 私有辅助 ====================

    private String currentUsername() {
        String u = UserContext.getUsername();
        if (u == null || u.isBlank() || "无用户参数".equals(u)) return null;
        return u;
    }

    private String convKey(String username, String sessionId) {
        return CONV_PREFIX + username + ":" + sessionId;
    }

    private List<Map<String, Object>> readIndex(String username) {
        try {
            Object raw = deepSeekRedisTemplate.opsForValue().get(IDX_PREFIX + username);
            if (raw == null) return new ArrayList<>();
            String json = raw instanceof String ? (String) raw : raw.toString();
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("读取会话索引失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeIndex(String username, List<Map<String, Object>> metas) {
        try {
            List<Map<String, Object>> capped = metas.size() > MAX_SESSIONS
                    ? new ArrayList<>(metas.subList(0, MAX_SESSIONS)) : metas;
            String key = IDX_PREFIX + username;
            deepSeekRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(capped));
            deepSeekRedisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            log.warn("写入会话索引失败: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> readConv(String username, String sessionId) {
        try {
            Object raw = deepSeekRedisTemplate.opsForValue().get(convKey(username, sessionId));
            if (raw == null) return new ArrayList<>();
            String json = raw instanceof String ? (String) raw : raw.toString();
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("读取会话内容失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private long asLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        return 0L;
    }

    private String deriveTitle(List<Map<String, Object>> messages) {
        for (Map<String, Object> m : messages) {
            if ("user".equals(m.get("role"))) {
                String c = (String) m.get("content");
                if (c != null && !c.isBlank()) {
                    c = c.trim();
                    return c.length() > 20 ? c.substring(0, 20) : c;
                }
            }
        }
        return "新对话";
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
        }
        return rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody;
    }
}
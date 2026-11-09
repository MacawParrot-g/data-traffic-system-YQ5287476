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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
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
    private static final int MAX_IMAGES_PER_MSG = 5;
    private static final int MAX_IMAGE_BASE64_CHARS = 12 * 1024 * 1024;
    private static final int MAX_SESSIONS = 50;
    private volatile Map<String, Object> cachedBalance;
    private volatile long cachedBalanceAt;
    private static final long BALANCE_CACHE_MILLIS = 30_000L;
    private static final int URL_FETCH_MAX_CHARS = 2 * 1024 * 1024;

    @Qualifier("deepSeekRestTemplate")
    @Autowired
    private RestTemplate deepSeekRestTemplate;

    @Qualifier("deepSeekRedisTemplate")
    @Autowired
    private RedisTemplate<String, Object> deepSeekRedisTemplate;

    @Qualifier("restTemplate")
    @Autowired
    private RestTemplate restTemplate;

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
    public Result chat(List<Map<String, Object>> messages, String model, Double temperature, Integer maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail("DeepSeek API Key 未配置，请在服务器 .env 文件中设置 DEEPSEEK_API_KEY");
        }
        if (messages == null || messages.isEmpty()) {
            return Result.fail("对话内容不能为空");
        }

        String realModel = (model == null || model.isBlank()) ? defaultModel : model;

        // 仅 flash 模型（deepseek-v4-flash）支持图像输入
        boolean hasImages = messages.stream().anyMatch(m -> m.get("images") instanceof List && !((List<?>) m.get("images")).isEmpty());
        if (hasImages && !realModel.startsWith("deepseek-v4")) {
            return Result.fail("当前模型不支持图片识别，请切换到 deepseek-v4-flash 后重试");
        }

        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            Object role = m.get("role");
            if (role == null) return Result.fail("消息缺少 role 字段");
            Object text = m.get("content");
            List<String> images = (List<String>) m.get("images");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", role);
            if (images == null || images.isEmpty()) {
                item.put("content", text == null ? "" : String.valueOf(text));
            } else {
                if (images.size() > MAX_IMAGES_PER_MSG) {
                    return Result.fail("单条消息最多上传 " + MAX_IMAGES_PER_MSG + " 张图片");
                }
                List<Map<String, Object>> parts = new ArrayList<>();
                String txt = text == null ? "" : String.valueOf(text);
                if (!txt.isBlank()) {
                    Map<String, Object> tp = new LinkedHashMap<>();
                    tp.put("type", "text");
                    tp.put("text", txt);
                    parts.add(tp);
                }
                for (String img : images) {
                    if (img == null || !img.startsWith("data:image/") || !img.contains(";base64,")) {
                        return Result.fail("图片格式无效，仅支持 base64 编码的 image 数据");
                    }
                    if (img.length() > MAX_IMAGE_BASE64_CHARS) {
                        return Result.fail("单张图片过大，请压缩后重试（base64 不超过 10MB）");
                    }
                    Map<String, Object> ip = new LinkedHashMap<>();
                    ip.put("type", "image_url");
                    Map<String, Object> urlObj = new LinkedHashMap<>();
                    urlObj.put("url", img);
                    urlObj.put("detail", "auto");
                    ip.put("image_url", urlObj);
                    parts.add(ip);
                }
                item.put("content", parts);
            }
            apiMessages.add(item);
        }

        String url = baseUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", realModel);
        body.put("messages", apiMessages);
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

    @Override
    @SuppressWarnings("unchecked")
    public Result balance(boolean forceRefresh) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail("DeepSeek API Key 未配置，请在服务器 .env 文件中设置 DEEPSEEK_API_KEY");
        }

        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedBalance;
        if (!forceRefresh && cached != null && now - cachedBalanceAt < BALANCE_CACHE_MILLIS) {
            Map<String, Object> data = new LinkedHashMap<>(cached);
            data.put("cached", true);
            return Result.success("获取余额成功", data);
        }

        String url = baseUrl + "/user/balance";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = deepSeekRestTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> json = resp.getBody();
            if (json == null) {
                return Result.fail("DeepSeek 返回空响应");
            }

            Map<String, Object> data = normalizeBalance(json);
            cachedBalance = data;
            cachedBalanceAt = System.currentTimeMillis();
            return Result.success("获取余额成功", data);

        } catch (HttpStatusCodeException e) {
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            log.error("DeepSeek 余额查询失败, status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return Result.fail("余额查询失败(" + e.getStatusCode().value() + ")：" + detail);
        } catch (Exception e) {
            log.error("DeepSeek 余额查询异常: {}", e.getMessage());
            return Result.fail("余额查询异常：" + e.getMessage());
        }
    }

    @Override
    public Result fetchUrl(String url) {
        if (url == null || url.isBlank()) return Result.fail("URL 不能为空");
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            return Result.fail("URL 格式非法：" + e.getMessage());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Result.fail("仅支持 http/https 协议");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) return Result.fail("URL 缺少主机名");
        if (isInternalHost(uri.getHost())) return Result.fail("出于安全考虑，禁止访问内网地址");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) UrlPreviewProxy/1.0");
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON, MediaType.ALL));

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    uri.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = resp.getBody() == null ? "" : resp.getBody();
            MediaType ct = resp.getHeaders().getContentType();
            String ctValue = ct == null ? "" : ct.toString();

            boolean truncated = body.length() > URL_FETCH_MAX_CHARS;
            if (truncated) body = body.substring(0, URL_FETCH_MAX_CHARS);

            String head = body.substring(0, Math.min(body.length(), 64)).trim().toLowerCase();
            Object parsedJson = null;
            if (ctValue.contains("json") || head.startsWith("{") || head.startsWith("[")) {
                try { parsedJson = objectMapper.readValue(body, Object.class); } catch (Exception ignore) { }
            }

            String mode;
            if (parsedJson != null) mode = "json";
            else if (ctValue.contains("html") || head.startsWith("<!doctype") || head.startsWith("<html")) mode = "html";
            else mode = "text";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", uri.toString());
            data.put("httpStatus", resp.getStatusCode().value());
            data.put("contentType", ctValue);
            data.put("mode", mode);
            data.put("truncated", truncated);
            if (parsedJson != null) data.put("json", parsedJson);
            else data.put("text", body);
            return Result.success("抓取成功", data);
        } catch (HttpStatusCodeException e) {
            return Result.fail("目标地址返回异常：" + e.getStatusCode().value() + " " + previewOf(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("URL 抓取失败: {}", e.getMessage());
            return Result.fail("URL 抓取失败：" + e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

    /** 解析目标主机并拦截环回/内网/链路本地地址，防止 SSRF。 */
    private boolean isInternalHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 域名解析失败按拒绝处理，避免绕过
            return true;
        }
        return false;
    }

    private String previewOf(String body) {
        if (body == null || body.isBlank()) return "(空响应体)";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
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
            // 会话数据永久保存：SET 不带过期时间，同时会清除该 key 历史上残留的 TTL
            deepSeekRedisTemplate.opsForValue().set(ck, objectMapper.writeValueAsString(new ArrayList<>()));
            // Deleted:deepSeekRedisTemplate.expire(ck, SESSION_TTL);
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

        long now = System.currentTimeMillis();
        List<Map<String, Object>> clean = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (Boolean.TRUE.equals(m.get("pending"))) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("role", m.get("role"));
            c.put("content", m.get("content"));
            if (m.get("reasoning") != null) c.put("reasoning", m.get("reasoning"));
            if (m.get("usage") != null) c.put("usage", m.get("usage"));
            if (m.get("error") != null) c.put("error", m.get("error"));
            if (m.get("expiresAt") instanceof Number) {
                long expiresAt = ((Number) m.get("expiresAt")).longValue();
                if (expiresAt <= now) continue;
                c.put("expiresAt", expiresAt);
            }
            clean.add(c);
        }

        try {
            String ck = convKey(username, sessionId);
            List<Map<String, Object>> cleaned = filterExpired(username, sessionId, clean);
            deepSeekRedisTemplate.opsForValue().set(ck, objectMapper.writeValueAsString(cleaned));
            clean = cleaned;
        } catch (Exception e) {
            return Result.fail("保存会话失败：" + e.getMessage());
        }

        List<Map<String, Object>> metas = readIndex(username);
        Map<String, Object> target = null;
        for (Map<String, Object> m : metas) {
            if (sessionId.equals(m.get("id"))) { target = m; break; }
        }
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

    @Override
    public Result setMessageTtl(String sessionId, Integer messageIndex, Long ttlSeconds) {
        String username = currentUsername();
        if (username == null) return Result.fail("未获取到用户身份");
        if (sessionId == null || sessionId.isBlank()) return Result.fail("会话ID不能为空");
        if (messageIndex == null || messageIndex < 0) return Result.fail("消息索引不能为空");

        List<Map<String, Object>> messages = readConv(username, sessionId);
        if (messageIndex >= messages.size()) return Result.fail("消息索引超出范围");

        Map<String, Object> msg = messages.get(messageIndex);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            msg.remove("expiresAt");
        } else {
            msg.put("expiresAt", System.currentTimeMillis() + ttlSeconds * 1000L);
        }

        try {
            deepSeekRedisTemplate.opsForValue().set(convKey(username, sessionId), objectMapper.writeValueAsString(messages));
        } catch (Exception e) {
            return Result.fail("设置过期时间失败：" + e.getMessage());
        }
        return Result.success(ttlSeconds == null || ttlSeconds <= 0 ? "已取消过期，该回复永久保存" : "已设置该回复的过期时间", msg);
    }

    // ==================== 私有辅助 ====================

    private String currentUsername() {
        String u = UserContext.getUsername();
        if (u == null || u.isBlank() || "无用户参数".equals(u)) return null;
        return u;
    }

    private List<Map<String, Object>> filterExpired(String username, String sessionId, List<Map<String, Object>> msgs) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> valid = new ArrayList<>();
        boolean expiredRemoved = false;
        for (Map<String, Object> m : msgs) {
            if (m.get("expiresAt") instanceof Number && ((Number) m.get("expiresAt")).longValue() <= now) {
                expiredRemoved = true;
                continue;
            }
            valid.add(m);
        }
        if (expiredRemoved) {
            try {
                deepSeekRedisTemplate.opsForValue().set(convKey(username, sessionId), objectMapper.writeValueAsString(valid));
            } catch (Exception e) {
                log.warn("清理过期回复失败: {}", e.getMessage());
            }
        }
        return valid;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeBalance(Map<String, Object> json) {
        List<Map<String, Object>> rawInfos = (List<Map<String, Object>>) json.get("balance_infos");
        List<Map<String, Object>> items = new ArrayList<>();
        double total = 0d;
        double granted = 0d;
        double toppedUp = 0d;
        String currency = null;

        if (rawInfos != null) {
            for (Map<String, Object> info : rawInfos) {
                String cur = info.get("currency") == null ? null : String.valueOf(info.get("currency"));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("currency", cur);
                item.put("totalBalance", round2(parseAmount(info.get("total_balance"))));
                item.put("grantedBalance", round2(parseAmount(info.get("granted_balance"))));
                item.put("toppedUpBalance", round2(parseAmount(info.get("topped_up_balance"))));
                items.add(item);

                total += parseAmount(info.get("total_balance"));
                granted += parseAmount(info.get("granted_balance"));
                toppedUp += parseAmount(info.get("topped_up_balance"));
                if (currency == null) currency = cur;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("isAvailable", Boolean.TRUE.equals(json.get("is_available")));
        data.put("currency", currency == null ? "CNY" : currency);
        data.put("totalBalance", round2(total));
        data.put("grantedBalance", round2(granted));
        data.put("toppedUpBalance", round2(toppedUp));
        data.put("balanceInfos", items);
        data.put("fetchedAt", System.currentTimeMillis());
        data.put("cached", false);
        return data;
    }

    private double parseAmount(Object v) {
        if (v == null) return 0d;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
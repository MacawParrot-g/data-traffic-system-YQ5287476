package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.config.RedisDistributedLock;
import org.example.entity.Template;
import org.example.mapper.TemplateMapper;
import org.example.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TemplateServiceImpl implements TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateServiceImpl.class);

    public static final String TYPE_EXCEPTION = "异常类型";
    public static final String TYPE_REMARK = "备注模板";
    public static final String TYPE_FROZEN = "冻结理由";
    public static final String TYPE_FROZEN_FORBIDDEN = "冻结理由屏蔽词";
    public static final Set<String> ALLOWED_TYPES = Set.of(TYPE_EXCEPTION, TYPE_REMARK, TYPE_FROZEN, TYPE_FROZEN_FORBIDDEN);

    private static final String KEY_PREFIX = "template:options:";
    private static final String WARM_LOCK_KEY = "lock:template:warmup";
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);
    private static final long MIN_TTL_SECONDS = 10;
    private static final long MAX_TTL_SECONDS = 24 * 3600;
    private static final long PRESSURE_COOLDOWN_MS = 10 * 60 * 1000;

    // MySQL压力阈值：活跃连接 >= 8 或 连接占用率 >= 60% 视为压力大
    private static final int PRESSURE_RUNNING_THREADS = 8;
    private static final double PRESSURE_USAGE_RATIO = 0.6;

    private static final Map<String, List<String>> SEED_DATA = new LinkedHashMap<>();

    static {
        SEED_DATA.put(TYPE_EXCEPTION, List.of("正常", "iOS16闪退", "iOS13/14/16均闪退", "需要iOS18以上",
                "地区不支持", "硬件版本过低", "超过10分钟0上报", "越狱检测", "其他", "验证已解决", "测试"));
        SEED_DATA.put(TYPE_REMARK, List.of("需要iOS17以上", "需要登陆后使用，无法注册", "卡死在加载页进不去",
                "非英语汉语软件，看不懂", "网络检测，无法进入", "需要订阅后使用", "事件机制成因不明", "逻辑混乱，无法正常游玩或使用"));
        SEED_DATA.put(TYPE_FROZEN, List.of("需要iOS17以上", "需要登陆后使用，无法注册", "卡死在加载页进不去",
                "非英语汉语软件，看不懂", "网络检测，无法进入", "需要订阅后使用", "事件机制成因不明", "逻辑混乱，无法正常游玩或使用",
                "iOS16闪退", "iOS13/14/16均闪退", "需要iOS18以上", "地区不支持", "硬件版本过低", "超过10分钟0上报", "越狱检测", "其他", "验证已解决"));
        SEED_DATA.put(TYPE_FROZEN_FORBIDDEN, List.of("iOS16闪退", "iOS13/14/16均闪退", "需要iOS18以上", "地区不支持",
                "硬件版本过低", "超过10分钟0上报", "越狱检测", "其他", "验证已解决"));
    }

    @Autowired
    private TemplateMapper templateMapper;

    @Autowired
    @Qualifier("templateRedisTemplate")
    private RedisTemplate<String, Object> templateRedisTemplate;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Autowired
    private ObjectMapper objectMapper;

    private volatile long lastAutoWarmTime = 0;

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    @Override
    public Result getOptionsByType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            return Result.fail("无效的类型");
        }
        String key = KEY_PREFIX + type;
        try {
            Object cached = templateRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<String> list = objectMapper.readValue(cached.toString(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                return Result.success("查询成功（Redis缓存）", list);
            }
        } catch (Exception e) {
            log.warn("模板缓存读取失败，回退MySQL: {}", e.getMessage());
        }
        List<String> content = templateMapper.selectContentByType(type);
        return Result.success("查询成功（数据库）", content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addTemplate(String content, String type, String writer) {
        if (content == null || content.trim().isEmpty()) return Result.fail("内容不能为空");
        if (type == null || !ALLOWED_TYPES.contains(type)) return Result.fail("无效的类型");
        String trimmed = content.trim();
        List<Template> existing = templateMapper.selectByType(type);
        for (Template t : existing) {
            if (trimmed.equals(t.getContent())) {
                return Result.fail("该类型下已存在相同内容");
            }
        }
        Template record = new Template();
        record.setHash(generateUniqueHash());
        record.setContent(trimmed);
        record.setType(type);
        record.setWriter(writer != null && !writer.isBlank() ? writer : "unknown");
        templateMapper.insertTemplate(record);
        evictCache(type);
        log.info("✅ 新增模板成功: type={}, content={}, writer={}", type, trimmed, record.getWriter());
        return Result.success("新增成功", record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateTemplate(String hash, String content, String type) {
        if (hash == null || hash.isEmpty()) return Result.fail("hash不能为空");
        if (content == null || content.trim().isEmpty()) return Result.fail("内容不能为空");
        if (type == null || !ALLOWED_TYPES.contains(type)) return Result.fail("无效的类型");
        Template existing = templateMapper.selectById(hash);
        if (existing == null) return Result.fail("该模板记录不存在");
        String oldType = existing.getType();
        existing.setContent(content.trim());
        existing.setType(type);
        // writer 字段不允许修改，保持原值
        templateMapper.updateTemplate(existing);
        evictCache(oldType);
        if (!oldType.equals(type)) evictCache(type);
        log.info("✅ 更新模板成功: hash={}, type={}, content={}", hash, type, existing.getContent());
        return Result.success("更新成功", existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteTemplate(String hash) {
        if (hash == null || hash.isEmpty()) return Result.fail("hash不能为空");
        Template existing = templateMapper.selectById(hash);
        if (existing == null) return Result.fail("该模板记录不存在");
        templateMapper.deleteByHash(hash);
        evictCache(existing.getType());
        log.info("🗑️ 删除模板成功: hash={}, type={}, content={}", hash, existing.getType(), existing.getContent());
        return Result.success("删除成功", null);
    }

    @Override
    @Transactional(readOnly = true)
    public Result searchTemplates(String type, String keyword, String writer, int page, int size) {
        int offset = (page - 1) * size;
        List<Template> list = templateMapper.searchTemplates(type, keyword, writer, size, offset);
        long total = templateMapper.countSearch(type, keyword, writer);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success("查询成功，共 " + total + " 条", result);
    }

    @Override
    public Result manualWarmUp(Long ttlSeconds) {
        long ttl = (ttlSeconds == null || ttlSeconds <= 0) ? DEFAULT_TTL.getSeconds() : ttlSeconds;
        if (ttl < MIN_TTL_SECONDS || ttl > MAX_TTL_SECONDS) {
            return Result.fail("TTL需在 " + MIN_TTL_SECONDS + " 秒 ~ " + MAX_TTL_SECONDS + " 秒之间");
        }
        int count = warmUpAllTypes(Duration.ofSeconds(ttl));
        return Result.success("手动预热完成，共 " + count + " 条写入Redis db4，TTL=" + ttl + "秒", count);
    }

    @Override
    public void seedIfEmpty() {
        try {
            long total = templateMapper.countAll();
            if (total > 0) return;
            int count = 0;
            for (Map.Entry<String, List<String>> entry : SEED_DATA.entrySet()) {
                for (String content : entry.getValue()) {
                    Template t = new Template();
                    t.setHash(generateUniqueHash());
                    t.setContent(content);
                    t.setType(entry.getKey());
                    t.setWriter("system");
                    templateMapper.insertTemplate(t);
                    count++;
                }
            }
            log.info("🌱 template表为空，已初始化种子数据 {} 条", count);
        } catch (Exception e) {
            log.error("❌ template种子数据初始化失败: {}", e.getMessage());
        }
    }

    private int warmUpAllTypes(Duration ttl) {
        int total = 0;
        for (String type : ALLOWED_TYPES) {
            List<String> contents = templateMapper.selectContentByType(type);
            try {
                templateRedisTemplate.opsForValue().set(KEY_PREFIX + type,
                        objectMapper.writeValueAsString(contents), ttl);
                total += contents.size();
            } catch (Exception e) {
                log.warn("模板预热写入Redis失败 type={}: {}", type, e.getMessage());
            }
        }
        return total;
    }

    /**
     * MySQL压力监测：压力大时自动将模板数据批量预热到Redis db4（默认TTL 2小时）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void pressureCheckTask() {
        try {
            if (!isMySQLUnderPressure()) return;
            long now = System.currentTimeMillis();
            if (now - lastAutoWarmTime < PRESSURE_COOLDOWN_MS) return;

            String lockValue = UUID.randomUUID().toString();
            if (!redisDistributedLock.tryLock(WARM_LOCK_KEY, lockValue, Duration.ofMinutes(2))) {
                return;
            }
            try {
                boolean needWarm = false;
                for (String type : ALLOWED_TYPES) {
                    Long remain = templateRedisTemplate.getExpire(KEY_PREFIX + type);
                    if (remain == null || remain < 0
                            || remain < DEFAULT_TTL.getSeconds() / 2) {
                        needWarm = true;
                        break;
                    }
                }
                if (!needWarm) return;
                int count = warmUpAllTypes(DEFAULT_TTL);
                lastAutoWarmTime = System.currentTimeMillis();
                log.info("🔥 MySQL压力增大，已自动预热 {} 条模板数据到Redis db4（TTL=2小时）", count);
            } finally {
                redisDistributedLock.unlock(WARM_LOCK_KEY, lockValue);
            }
        } catch (Exception e) {
            log.warn("MySQL压力检测/自动预热失败: {}", e.getMessage());
        }
    }

    private boolean isMySQLUnderPressure() {
        try {
            Map<String, Object> vars = jdbcTemplate().queryForMap(
                    "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME IN ('Threads_connected','Threads_running')");
            long connected = Long.parseLong(String.valueOf(vars.get("Threads_connected")));
            long running = Long.parseLong(String.valueOf(vars.get("Threads_running")));
            long poolSize = 20;
            try {
                String cfg = jdbcTemplate().queryForObject("SHOW VARIABLES LIKE 'max_connections'",
                        (rs, i) -> rs.getString(2));
                if (cfg != null) poolSize = Long.parseLong(cfg.trim());
            } catch (Exception ignored) {
            }
            double usageRatio = poolSize > 0 ? (double) connected / poolSize : 0;
            return running >= PRESSURE_RUNNING_THREADS || usageRatio >= PRESSURE_USAGE_RATIO;
        } catch (Exception e) {
            log.warn("MySQL压力指标查询失败: {}", e.getMessage());
            return false;
        }
    }

    private void evictCache(String type) {
        try {
            templateRedisTemplate.delete(KEY_PREFIX + type);
        } catch (Exception e) {
            log.warn("模板缓存失效失败 type={}: {}", type, e.getMessage());
        }
    }

    private String generateUniqueHash() {
        for (int i = 0; i < 10; i++) {
            String hash = generateHash();
            if (templateMapper.selectById(hash) == null) return hash;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateHash() {
        try {
            long ts = System.currentTimeMillis();
            int rand = ThreadLocalRandom.current().nextInt(100000, 999999);
            String raw = "template" + ts + rand + UUID.randomUUID();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
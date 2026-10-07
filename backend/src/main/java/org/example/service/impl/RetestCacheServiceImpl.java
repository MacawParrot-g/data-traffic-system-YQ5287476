package org.example.service.impl;

import org.example.common.Result;
import org.example.mapper.RetestMapper;
import org.example.service.RetestCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Order(2)
public class RetestCacheServiceImpl implements RetestCacheService, CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RetestCacheServiceImpl.class);
    private static final String RETEST_CACHE_KEY = "retest:cache";
    private static final long CACHE_TTL_HOURS = 24;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private RetestMapper retestMapper;

    @Autowired
    @Qualifier("retestRedisTemplate")
    private RedisTemplate<String, Object> retestRedisTemplate;

    @Override
    public void run(String... args) {
        log.info("🚀 服务启动，开始预热复测数据到Redis DB12...");
        warmUp();
    }

    @Override
    public int warmUp() {
        try {
            String startDate = LocalDate.now().minusDays(2).format(DATE_FMT);
            List<Map<String, Object>> candidates = retestMapper.selectRetestCandidates(startDate);
            retestRedisTemplate.delete(RETEST_CACHE_KEY);
            if (candidates == null || candidates.isEmpty()) {
                log.info("🔄 复测缓存预热完成（过去3天无B级以上数据，Redis DB12已清空）");
                return 0;
            }
            for (Map<String, Object> c : candidates) {
                Object bid = c.get("bundleId");
                Object grade = c.get("grade");
                if (bid != null) {
                    retestRedisTemplate.opsForHash().put(RETEST_CACHE_KEY,
                            String.valueOf(bid), grade != null ? String.valueOf(grade) : "");
                }
            }
            retestRedisTemplate.expire(RETEST_CACHE_KEY, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("🔄 复测缓存预热完成，共加载 {} 条B级以上bundleId到Redis DB12，TTL={}小时", candidates.size(), CACHE_TTL_HOURS);
            return candidates.size();
        } catch (Exception e) {
            log.error("❌ 复测缓存预热失败: {}", e.getMessage());
            return 0;
        }
    }

    @Scheduled(fixedDelay = 600000, initialDelay = 600000)
    public void scheduledReWarmUp() {
        try {
            Boolean hasKey = retestRedisTemplate.hasKey(RETEST_CACHE_KEY);
            if (!Boolean.TRUE.equals(hasKey)) {
                log.info("⏰ 检测到复测缓存键已集体过期，开始重新预热...");
                warmUp();
            }
        } catch (Exception e) {
            log.error("❌ 复测缓存定时检查失败: {}", e.getMessage());
        }
    }

    @Override
    public Result manualWarmUp() {
        int count = warmUp();
        return Result.success("预热完成，共加载 " + count + " 条B级以上bundleId", count);
    }

    @Override
    public Result randomBundle() {
        try {
            Set<Object> keys = retestRedisTemplate.opsForHash().keys(RETEST_CACHE_KEY);
            if (keys == null || keys.isEmpty()) {
                return Result.fail("Redis中暂无可复测数据，请先在管理员页面手动预热");
            }
            List<Object> list = new ArrayList<>(keys);
            String bundleId = String.valueOf(list.get(ThreadLocalRandom.current().nextInt(list.size())));
            Object grade = retestRedisTemplate.opsForHash().get(RETEST_CACHE_KEY, bundleId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bundleId", bundleId);
            data.put("grade", grade != null ? String.valueOf(grade) : "");
            return Result.success("获取复测bundleId成功", data);
        } catch (Exception e) {
            log.error("❌ 随机获取复测bundleId失败: {}", e.getMessage());
            return Result.fail("获取复测数据失败：" + e.getMessage());
        }
    }

    @Override
    public Result listCache() {
        try {
            Map<Object, Object> entries = retestRedisTemplate.opsForHash().entries(RETEST_CACHE_KEY);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bundleId", String.valueOf(e.getKey()));
                item.put("grade", e.getValue() != null ? String.valueOf(e.getValue()) : "");
                list.add(item);
            }
            Long ttl = retestRedisTemplate.getExpire(RETEST_CACHE_KEY, TimeUnit.SECONDS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("list", list);
            result.put("total", list.size());
            result.put("ttlSeconds", ttl != null && ttl > 0 ? ttl : 0);
            return Result.success("查询成功，共 " + list.size() + " 条", result);
        } catch (Exception e) {
            log.error("❌ 查询复测缓存失败: {}", e.getMessage());
            return Result.fail("查询失败：" + e.getMessage());
        }
    }

    @Override
    public Result addBundle(String bundleId, String grade) {
        if (bundleId == null || bundleId.trim().isEmpty()) return Result.fail("bundleId不能为空");
        try {
            retestRedisTemplate.opsForHash().put(RETEST_CACHE_KEY, bundleId.trim(), grade != null ? grade : "");
            return Result.success("添加成功", null);
        } catch (Exception e) {
            log.error("❌ 新增复测缓存失败: {}", e.getMessage());
            return Result.fail("添加失败：" + e.getMessage());
        }
    }

    @Override
    public Result updateBundle(String bundleId, String grade) {
        if (bundleId == null || bundleId.trim().isEmpty()) return Result.fail("bundleId不能为空");
        try {
            Boolean exists = retestRedisTemplate.opsForHash().hasKey(RETEST_CACHE_KEY, bundleId.trim());
            if (!Boolean.TRUE.equals(exists)) return Result.fail("该bundleId不存在于复测缓存中");
            retestRedisTemplate.opsForHash().put(RETEST_CACHE_KEY, bundleId.trim(), grade != null ? grade : "");
            return Result.success("更新成功", null);
        } catch (Exception e) {
            log.error("❌ 更新复测缓存失败: {}", e.getMessage());
            return Result.fail("更新失败：" + e.getMessage());
        }
    }

    @Override
    public Result deleteBundle(String bundleId) {
        if (bundleId == null || bundleId.trim().isEmpty()) return Result.fail("bundleId不能为空");
        try {
            Long removed = retestRedisTemplate.opsForHash().delete(RETEST_CACHE_KEY, bundleId.trim());
            if (removed == null || removed == 0) return Result.fail("该bundleId不存在");
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("❌ 删除复测缓存失败: {}", e.getMessage());
            return Result.fail("删除失败：" + e.getMessage());
        }
    }

    @Override
    public Result clearCache() {
        try {
            retestRedisTemplate.delete(RETEST_CACHE_KEY);
            return Result.success("已清空复测缓存", null);
        } catch (Exception e) {
            log.error("❌ 清空复测缓存失败: {}", e.getMessage());
            return Result.fail("清空失败：" + e.getMessage());
        }
    }
}

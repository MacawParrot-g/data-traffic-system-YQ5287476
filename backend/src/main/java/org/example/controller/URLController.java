package org.example.controller;

import org.example.annotation.*;
import org.example.common.Result;
import org.example.service.URLService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;

@RestController
public class URLController {

    @Autowired
    private URLService urlService;

    private static final Logger log = LoggerFactory.getLogger(URLController.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${upstream.base-url}")
    private String upstreamBaseUrl;

    @GetMapping("/api/proxy/task")
    @LogExecutionTime("自动模式拉取任务")
    @SkipRateLimit
    @Retryable(maxAttempts=3, delay=500, backoffMultiplier=2.0, resultCheck=true)
    @Timeout(value=8, unit=SECONDS)
    public Result proxyTask() {
        Map<String, Object> map = urlService.proxyTask();
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/obtain")
    @LogExecutionTime("手动模式拉取任务")
    @Retryable(maxAttempts=3, delay=500, backoffMultiplier=2.0, resultCheck=true)
    @Timeout(value=8, unit=SECONDS)
    public Result proxyObtain(@RequestParam Long appleid) {
        Map<String, Object> map = urlService.proxyObtain(appleid);
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/event")
    @LogExecutionTime("获取事件")
    @Retryable(maxAttempts=5, delay=300)
    @Timeout(value = 8,unit=SECONDS)
    public Result proxyEvent(@RequestParam String bundleId) {
        Map<String, Object> map = urlService.proxyEvent(bundleId);
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/attribution")
    @LogExecutionTime("获取事件归因")
    @Retryable(maxAttempts=5, delay=300)
    @Timeout(value = 8,unit=SECONDS)
    public Result proxyAttribution(@RequestParam String bundleId, @RequestParam String type) {
        Map<String, Object> map = urlService.proxyAttribution(bundleId, type);
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/check-version")
    @LogExecutionTime("停止版本下发")
    @Timeout(value = 8,unit=SECONDS)
    public Result proxyCheckVersion(@RequestParam String appId, @RequestParam String bundleId, @RequestParam String checkVersion) {
        Map<String, Object> map = urlService.proxyCheckVersion(appId, bundleId, checkVersion);
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/frozen")
    @LogExecutionTime("冻结任务")
    @Retryable(maxAttempts=3)
    @Timeout(value = 6,unit=SECONDS)
    public Result proxyFrozen(@RequestParam Long id) {
        Map<String, Object> map = urlService.proxyFrozen(id);
        return mapToResult(map);
    }

    @GetMapping("/api/proxy/task/count")
    @LogExecutionTime("获取库存数量")
    @SkipRateLimit
    @MethodCache(ttl=30)
    @Retryable(maxAttempts=2)
    @Timeout(value = 3,unit=SECONDS)
    public Result proxyTaskCount() {
        Map<String, Object> map = urlService.proxyTaskCount();
        return mapToResult(map);
    }

    @PostMapping("/api/proxy/frozen/v2")
    @LogExecutionTime("上传冻结数据(新版)")
    @Retryable(maxAttempts=3)
    @Timeout(value = 6, unit = SECONDS)
    public Result proxyFrozenV2(@RequestBody Map<String, Object> body) {
        String remoteUrl = upstreamBaseUrl + "/ad/play/task/frozen/v2";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ResponseEntity<Map> response = restTemplate.postForEntity(remoteUrl, entity, Map.class);
            Map<String, Object> map = response.getBody();
            log.info("冻结V2远程响应 | status={} | body={}", response.getStatusCode(), map);
            boolean success = Boolean.TRUE.equals(map == null ? null : map.get("success"));
            String remoteMsg = map != null && map.get("resultMsg") != null ? map.get("resultMsg").toString() : null;
            if (!success) {
                String msg = remoteMsg != null ? remoteMsg : "操作失败";
                if (map != null && map.get("resultCode") != null) {
                    msg = msg + "（resultCode=" + map.get("resultCode") + "）";
                }
                log.warn("冻结V2远程业务失败 | message={}", msg);
                return Result.fail("冻结数据上传失败：" + msg);
            }
            return Result.success(remoteMsg != null ? remoteMsg : "冻结数据上传成功", map.get("data"));
        } catch (Exception e) {
            log.error("冻结V2远程调用异常 | url={} | body={} | 异常类型={} | 信息={}", remoteUrl, body, e.getClass().getSimpleName(), e.getMessage());
            return Result.fail("冻结数据上传失败：" + e.getMessage());
        }
    }

    @PostMapping("/api/proxy/current-version")
    @LogExecutionTime("停止二次下发")
    @Timeout(value = 8, unit = SECONDS)
    public Result proxyCurrentVersion(@RequestBody Map<String, Object> body) {
        String remoteUrl = upstreamBaseUrl + "/ad/event/target/currentVersion";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings({"rawtypes", "unchecked"})
            ResponseEntity<Map> response = restTemplate.postForEntity(remoteUrl, entity, Map.class);
            Map<String, Object> map = response.getBody();
            log.info("停止二次下发远程响应 | status={} | body={}", response.getStatusCode(), map);
            boolean success = Boolean.TRUE.equals(map == null ? null : map.get("success"));
            String remoteMsg = map != null && map.get("resultMsg") != null ? map.get("resultMsg").toString() : null;
            if (!success) {
                String msg = remoteMsg != null ? remoteMsg : "操作失败";
                if (map != null && map.get("resultCode") != null) {
                    msg = msg + "（resultCode=" + map.get("resultCode") + "）";
                }
                log.warn("停止二次下发业务失败 | message={}", msg);
                return Result.fail("停止二次下发失败：" + msg);
            }
            return Result.success(remoteMsg != null ? remoteMsg : "停止二次下发成功", map.get("data"));
        } catch (Exception e) {
            log.error("停止二次下发远程调用异常 | url={} | body={} | 异常类型={} | 信息={}", remoteUrl, body, e.getClass().getSimpleName(), e.getMessage());
            return Result.fail("停止二次下发失败：" + e.getMessage());
        }
    }
    private Result mapToResult(Map<String, Object> map) {
        if (map == null) {
            return Result.fail("远程接口返回空响应，请稍后重试");
        }
        boolean success = Boolean.TRUE.equals(map.get("success"));
        String message = map.get("resultMsg") != null ? map.get("resultMsg").toString() : (success ? "操作成功" : "操作失败");
        Object data = map.get("data");
        if (success) {
            return Result.success(message, data);
        } else {
            return Result.fail(message);
        }
    }
}
package org.example.controller;

import org.example.annotation.*;
import org.example.service.URLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;

@RestController
public class URLController {

    @Autowired
    private URLService urlService;

    @GetMapping("/api/proxy/task")
    @LogExecutionTime("代理获取任务")
    @SkipRateLimit
    @Retryable(maxAttempts=3, delay=500, backoffMultiplier=2.0, resultCheck=true)
    @Timeout(value=8, unit=SECONDS)
    public Map<String, Object> proxyTask() {
        return urlService.proxyTask();
    }

    @GetMapping("/api/proxy/obtain")
    @LogExecutionTime("代理获取详情")
    @Retryable(maxAttempts=3, delay=500, backoffMultiplier=2.0, resultCheck=true)
    @Timeout(value=8, unit=SECONDS)
    public Map<String, Object> proxyObtain(@RequestParam Long appleid) {
        return urlService.proxyObtain(appleid);
    }

    @GetMapping("/api/proxy/event")
    @LogExecutionTime("代理获取事件")
    @Retryable(maxAttempts=5, delay=300)
    @Timeout(value = 8,unit=SECONDS)
    public Map<String, Object> proxyEvent(@RequestParam String bundleId) {
        return urlService.proxyEvent(bundleId);
    }

    @GetMapping("/api/proxy/attribution")
    @LogExecutionTime("代理获取归因")
    @Retryable(maxAttempts=5, delay=300)
    @Timeout(value = 8,unit=SECONDS)
    public Map<String, Object> proxyAttribution(@RequestParam String bundleId, @RequestParam String type) {
        return urlService.proxyAttribution(bundleId, type);
    }

    @GetMapping("/api/proxy/check-version")
    @LogExecutionTime("代理停止版本下发")
    @Timeout(value = 8,unit=SECONDS)
    public Map<String, Object> proxyCheckVersion(@RequestParam String appId, @RequestParam String bundleId, @RequestParam String checkVersion) {
        return urlService.proxyCheckVersion(appId, bundleId, checkVersion);
    }

    @GetMapping("/api/proxy/frozen")
    @LogExecutionTime("代理冻结任务")
    @Retryable(maxAttempts=3)
    @Timeout(value = 6,unit=SECONDS)
    public Map<String, Object> proxyFrozen(@RequestParam Long id) {
        return urlService.proxyFrozen(id);
    }

    @GetMapping("/api/proxy/task/count")
    @LogExecutionTime("代理获取库存数量")
    @SkipRateLimit
    @MethodCache(ttl=30)
    @Retryable(maxAttempts=2)
    @Timeout(value = 3,unit=SECONDS)
    public Map<String, Object> proxyTaskCount() {
        return urlService.proxyTaskCount();
    }
}

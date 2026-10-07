package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.RetestCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/retest")
public class RetestController {

    @Autowired
    private RetestCacheService retestCacheService;

    @GetMapping("/random-bundle")
    @LogExecutionTime("随机获取复测bundleId")
    public Result randomBundle() {
        return retestCacheService.randomBundle();
    }

    @GetMapping("/cache/list")
    @LogExecutionTime("查询复测缓存列表")
    public Result list() {
        return retestCacheService.listCache();
    }

    @PostMapping("/cache/warmup")
    @LogExecutionTime("手动预热复测缓存")
    public Result warmUp() {
        return retestCacheService.manualWarmUp();
    }

    @PostMapping("/cache/add")
    @LogExecutionTime("新增复测缓存")
    public Result add(@RequestBody Map<String, String> body) {
        return retestCacheService.addBundle(body.get("bundleId"), body.get("grade"));
    }

    @PutMapping("/cache/update")
    @LogExecutionTime("更新复测缓存")
    public Result update(@RequestBody Map<String, String> body) {
        return retestCacheService.updateBundle(body.get("bundleId"), body.get("grade"));
    }

    @DeleteMapping("/cache/delete")
    @LogExecutionTime("删除复测缓存")
    public Result delete(@RequestParam String bundleId) {
        return retestCacheService.deleteBundle(bundleId);
    }

    @DeleteMapping("/cache/clear")
    @LogExecutionTime("清空复测缓存")
    public Result clear() {
        return retestCacheService.clearCache();
    }
}

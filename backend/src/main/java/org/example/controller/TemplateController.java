package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.common.UserContext;
import org.example.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/template")
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    @GetMapping("/options")
    @LogExecutionTime("查询模板选项")
    public Result options(@RequestParam String type) {
        return templateService.getOptionsByType(type);
    }

    @PostMapping("/manage/add")
    @LogExecutionTime("备注管理-新增")
    public Result add(@RequestBody Map<String, String> body) {
        String writer = UserContext.getUsername();
        return templateService.addTemplate(body.get("content"), body.get("type"), writer);
    }

    @PutMapping("/manage/update")
    @LogExecutionTime("备注管理-更新")
    public Result update(@RequestBody Map<String, String> body) {
        return templateService.updateTemplate(body.get("hash"), body.get("content"), body.get("type"));
    }

    @DeleteMapping("/manage/delete")
    @LogExecutionTime("备注管理-删除")
    public Result delete(@RequestParam String hash) {
        return templateService.deleteTemplate(hash);
    }

    @PostMapping("/manage/search")
    @LogExecutionTime("备注管理-搜索")
    public Result search(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        String keyword = (String) body.get("keyword");
        String writer = (String) body.get("writer");
        int page = body.get("page") != null ? ((Number) body.get("page")).intValue() : 1;
        int size = body.get("size") != null ? ((Number) body.get("size")).intValue() : 15;
        return templateService.searchTemplates(type, keyword, writer, page, size);
    }

    @PostMapping("/manage/warmup")
    @LogExecutionTime("备注管理-手动预热")
    public Result warmup(@RequestBody(required = false) Map<String, Object> body) {
        Long ttlSeconds = (body == null || body.get("ttlSeconds") == null)
                ? null : ((Number) body.get("ttlSeconds")).longValue();
        return templateService.manualWarmUp(ttlSeconds);
    }
}
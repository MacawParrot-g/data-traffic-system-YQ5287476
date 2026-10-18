// 文件路径: backend/src/main/java/org/example/controller/PyScriptController.java
package org.example.controller;
import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.PyScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pyscript")
public class PyScriptController {

    @Autowired
    private PyScriptService pyScriptService;

    @GetMapping("/list")
    @LogExecutionTime("查询Python脚本列表")
    public Result listScripts() {
        return pyScriptService.listScripts();
    }

    @PostMapping("/execute")
    @LogExecutionTime("执行Python脚本")
    public Result executeScript(@RequestParam String scriptName) {
        return pyScriptService.executeScript(scriptName);
    }

    @PostMapping("/upload")
    @LogExecutionTime("上传Python脚本")
    public Result uploadScript(@RequestParam("file") MultipartFile file) {
        return pyScriptService.uploadScript(file);
    }

    @DeleteMapping("/delete")
    @LogExecutionTime("删除Python脚本")
    public Result deleteScript(@RequestParam String scriptName) {
        return pyScriptService.deleteScript(scriptName);
    }
}
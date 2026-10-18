// 文件路径: backend/src/main/java/org/example/service/PyScriptServiceImpl.java
package org.example.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.service.PyScriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class PyScriptServiceImpl implements PyScriptService {

    private static final Logger log = LoggerFactory.getLogger(PyScriptServiceImpl.class);

    @Value("${pyscript.script-dir:/opt/pyscript/sc}")
    private String scriptDir;

    @Value("${pyscript.python-bin:/opt/pyscript/venv/bin/python3}")
    private String pythonBin;

    private static final long SCRIPT_TIMEOUT_SECONDS = 120;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Result listScripts() {
        File dir = new File(scriptDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return Result.success("脚本目录为空或不存在", List.of());
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".py"));
        if (files == null || files.length == 0) {
            return Result.success("暂无脚本", List.of());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (File f : files) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", f.getName());
            info.put("size", f.length());
            info.put("lastModified", f.lastModified());
            list.add(info);
        }
        list.sort(Comparator.comparing(m -> (String) m.get("name")));
        return Result.success("查询成功", list);
    }

    @Override
    public Result executeScript(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            return Result.fail("脚本名称不能为空");
        }
        if (scriptName.contains("/") || scriptName.contains("\\") || scriptName.contains("..")) {
            return Result.fail("脚本名称包含非法字符");
        }

        Path scriptPath = Paths.get(scriptDir, scriptName);
        if (!Files.exists(scriptPath)) {
            return Result.fail("脚本不存在: " + scriptName);
        }

        long startTime = System.currentTimeMillis();
        try {
            String python = resolvePython();
            log.info("执行脚本: {} (python={})", scriptName, python);
            ProcessBuilder pb = new ProcessBuilder(python, scriptPath.toAbsolutePath().toString());
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptDir));

            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return Result.fail("脚本执行超时（" + SCRIPT_TIMEOUT_SECONDS + "s），已强制终止");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("exitCode", exitCode);
                errorData.put("stderr", stderr);
                errorData.put("stdout", stdout);
                errorData.put("elapsed", elapsed);
                Result failResult = Result.fail("脚本执行失败(exit=" + exitCode + "): " + stderr);
                failResult.setData(errorData);
                return failResult;
            }

            try {
                Map<String, Object> jsonResult = objectMapper.readValue(stdout.trim(), Map.class);
                jsonResult.put("_elapsed", elapsed);
                jsonResult.put("_exitCode", exitCode);
                return Result.success("脚本执行成功", jsonResult);
            } catch (Exception e) {
                Map<String, Object> rawData = new LinkedHashMap<>();
                rawData.put("rawOutput", stdout);
                rawData.put("stderr", stderr);
                rawData.put("elapsed", elapsed);
                rawData.put("exitCode", exitCode);
                return Result.success("脚本执行完毕（输出非JSON）", rawData);
            }

        } catch (Exception e) {
            log.error("执行Python脚本异常: {}", scriptName, e);
            return Result.fail("执行异常: " + e.getMessage());
        }
    }

    private String resolvePython() {
        Path configured = Paths.get(pythonBin);
        try {
            if (Files.exists(configured) && !Files.isSymbolicLink(configured)) {
                return pythonBin;
            }
            if (Files.isSymbolicLink(configured)) {
                Path target = Files.readSymbolicLink(configured);
                if (Files.exists(target)) {
                    return pythonBin;
                }
                log.warn("Python虚拟环境符号链接断裂: {} -> {}，降级使用系统python3", pythonBin, target);
            }
        } catch (Exception e) {
            log.warn("检查python路径异常: {}", e.getMessage());
        }
        log.info("使用系统python3替代venv（venv未创建或符号链接不可用）");
        return "python3";
    }

    @Override
    public Result uploadScript(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.endsWith(".py")) {
            return Result.fail("仅支持 .py 文件");
        }
        String safeName = originalName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        try {
            Path dir = Paths.get(scriptDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            file.transferTo(target.toFile());
            log.info("Python脚本上传成功: {} ({}字节)", safeName, file.getSize());
            return Result.success("脚本上传成功: " + safeName, Map.of("name", safeName, "size", file.getSize()));
        } catch (Exception e) {
            log.error("上传脚本失败", e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result deleteScript(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            return Result.fail("脚本名称不能为空");
        }
        if (scriptName.contains("/") || scriptName.contains("\\") || scriptName.contains("..")) {
            return Result.fail("脚本名称包含非法字符");
        }
        Path scriptPath = Paths.get(scriptDir, scriptName);
        try {
            if (Files.deleteIfExists(scriptPath)) {
                log.info("Python脚本已删除: {}", scriptName);
                return Result.success("脚本已删除: " + scriptName);
            } else {
                return Result.fail("脚本不存在: " + scriptName);
            }
        } catch (Exception e) {
            log.error("删除脚本失败: {}", scriptName, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
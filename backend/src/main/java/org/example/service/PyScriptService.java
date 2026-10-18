// 文件路径: backend/src/main/java/org/example/service/PyScriptService.java
package org.example.service;

import org.example.common.Result;
import org.springframework.web.multipart.MultipartFile;

public interface PyScriptService {
    Result listScripts();
    Result executeScript(String scriptName);
    Result uploadScript(MultipartFile file);
    Result deleteScript(String scriptName);
}
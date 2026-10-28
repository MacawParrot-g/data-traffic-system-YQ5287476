// 文件路径: backend/src/main/java/org/example/service/DeepSeekService.java
package org.example.service;

import org.example.common.Result;

import java.util.List;
import java.util.Map;

public interface DeepSeekService {

    /**
     * 调用 DeepSeek 对话补全接口（OpenAI 兼容格式）。
     *
     * @param messages    对话历史，每项形如 {role: "user"|"assistant"|"system", content: "..."}
     * @param model       模型名，为空则使用配置默认值
     * @param temperature 采样温度，可为空
     * @param maxTokens   最大生成 token 数，可为空
     */
    Result chat(List<Map<String, String>> messages, String model, Double temperature, Integer maxTokens);

    /** 返回配置状态（不暴露密钥）。 */
    Result status();
}
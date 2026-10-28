// 文件路径: backend/src/main/java/org/example/config/DeepSeekRestTemplateConfig.java
package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * DeepSeek 专用 RestTemplate。
 * LLM 生成耗时较长，独立于全局 restTemplate（响应超时 30s），这里读超时放宽到 120s。
 */
@Configuration
public class DeepSeekRestTemplateConfig {

    @Bean("deepSeekRestTemplate")
    public RestTemplate deepSeekRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return new RestTemplate(factory);
    }
}
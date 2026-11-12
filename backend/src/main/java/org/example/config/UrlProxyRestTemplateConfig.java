// 文件路径: backend/src/main/java/org/example/config/UrlProxyRestTemplateConfig.java
package org.example.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * URL 执行器 / 通用网页代理专用 RestTemplate。
 *
 * <p>与全局 {@code restTemplate}（被冻结/下发等 POST 复用）隔离，本 bean 只做只读 GET 抓取，因此：
 * <ul>
 *   <li>连接超时 5s、响应超时 15s，避免慢站/黑洞主机长时间占用 Tomcat 工作线程，
 *       也为可操作模式 iframe 的并发子资源预留更大连接池；</li>
 *   <li>关闭自动跟随重定向：代理在 {@code UrlProxyServiceImpl} 中对目标主机做 SSRF 校验，
 *       若底层自动 302 跳到内网地址就会绕过该校验，这里从传输层直接杜绝。</li>
 * </ul>
 */
@Configuration
public class UrlProxyRestTemplateConfig {

    @Bean("urlProxyRestTemplate")
    public RestTemplate urlProxyRestTemplate() {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(40)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 关闭自动重定向：由上层在每一跳重新校验目标，防止 302 指向内网绕过 SSRF 黑名单
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}

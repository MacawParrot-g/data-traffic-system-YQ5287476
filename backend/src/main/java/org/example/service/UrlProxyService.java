// 文件路径: C:\Users\EDY\data-traffic-system-YQ5287476\backend\src\main\java\org\example\service\UrlProxyService.java
package org.example.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface UrlProxyService {

    /** 反向代理目标站点资源，/api/url-proxy/{完整URL} 形式访问，HTML 会重写资源引用以支持页面内交互。 */
    ResponseEntity<byte[]> proxy(HttpServletRequest request);
}
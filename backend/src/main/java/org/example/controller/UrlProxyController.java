// 文件路径: C:\Users\EDY\data-traffic-system-YQ5287476\backend\src\main\java\org\example\controller\UrlProxyController.java
package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.annotation.LogExecutionTime;
import org.example.annotation.SkipRateLimit;
import org.example.service.UrlProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/url-proxy")
public class UrlProxyController {

    @Autowired
    private UrlProxyService urlProxyService;

    @RequestMapping("/**")
    @LogExecutionTime("URL代理请求")
    @SkipRateLimit
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        return urlProxyService.proxy(request);
    }
}
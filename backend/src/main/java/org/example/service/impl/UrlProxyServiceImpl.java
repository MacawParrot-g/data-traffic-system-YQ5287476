// 文件路径: C:\Users\EDY\data-traffic-system-YQ5287476\backend\src\main\java\org\example\service\impl\UrlProxyServiceImpl.java
package org.example.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.example.service.UrlProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UrlProxyServiceImpl implements UrlProxyService {

    private static final Logger log = LoggerFactory.getLogger(UrlProxyServiceImpl.class);

    private static final String PATH_PREFIX = "/api/url-proxy/";
    private static final int MAX_HTML_BYTES = 8 * 1024 * 1024;
    private static final Pattern HEAD_TAG = Pattern.compile("(?i)<head[^>]*>");
    private static final Pattern ROOT_RELATIVE = Pattern.compile("(?i)\\b(src|href|action)\\s*=\\s*\"/");

    @Qualifier("restTemplate")
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String target = extractTarget(request);
        if (target == null || !target.contains("://")) {
            return errorText(HttpStatusCode.valueOf(400), "缺少或非法的目标地址");
        }
        URI uri;
        try {
            uri = new URI(target);
        } catch (Exception e) {
            return errorText(HttpStatusCode.valueOf(400), "URL 格式非法：" + e.getMessage());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return errorText(HttpStatusCode.valueOf(400), "仅支持 http/https 协议");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || isInternalHost(uri.getHost())) {
            return errorText(HttpStatusCode.valueOf(403), "禁止访问内网地址或非法主机");
        }

        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) UrlProxy/1.0");
        reqHeaders.setAccept(List.of(MediaType.ALL));

        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    uri.toString(), HttpMethod.GET, new HttpEntity<>(reqHeaders), byte[].class);
            byte[] body = resp.getBody() == null ? new byte[0] : resp.getBody();
            MediaType ct = resp.getHeaders().getContentType();
            boolean html = (ct != null && ct.toString().contains("html")) || (ct == null && looksLikeHtml(body));

            if (html && body.length <= MAX_HTML_BYTES) {
                body = rewriteHtml(new String(body, StandardCharsets.UTF_8), uri)
                        .getBytes(StandardCharsets.UTF_8);
            }

            // 仅透传 Content-Type；剥离目标的 X-Frame-Options / CSP / Set-Cookie，保证可嵌入且不污染本站会话
            HttpHeaders out = new HttpHeaders();
            out.setContentType(html ? new MediaType("text", "html", StandardCharsets.UTF_8)
                    : (ct == null ? MediaType.APPLICATION_OCTET_STREAM : ct));
            out.setCacheControl("no-store");
            return new ResponseEntity<>(body, out, resp.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return errorText(e.getStatusCode(), "目标站点返回状态 " + e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("URL 代理抓取失败: {} -> {}", target, e.getMessage());
            return errorText(HttpStatusCode.valueOf(502), "代理抓取失败：" + e.getMessage());
        }
    }

    // ==================== 私有辅助 ====================

    /** 从 /api/url-proxy/https://host/path?query 中还原完整目标地址。 */
    private String extractTarget(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(PATH_PREFIX);
        if (idx < 0) return null;
        String rest = uri.substring(idx + PATH_PREFIX.length());
        String query = request.getQueryString();
        return (query == null || query.isEmpty()) ? rest : rest + "?" + query;
    }

    private ResponseEntity<byte[]> errorText(HttpStatusCode status, String msg) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(msg.getBytes(StandardCharsets.UTF_8));
    }


    /** 注入 <base>，并把根相对路径（src="/x"）重写为代理路径，保证子资源经本代理加载。 */
    private String rewriteHtml(String html, URI uri) {
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        String dir = path.endsWith("/") ? path : path.substring(0, path.lastIndexOf('/') + 1);
        String rootBase = UrlProxyServiceImpl.PATH_PREFIX + origin + "/";
        String baseHref = UrlProxyServiceImpl.PATH_PREFIX + origin + dir;

        String rewritten = UrlProxyServiceImpl.ROOT_RELATIVE.matcher(html)
                .replaceAll("$1=\"" + Matcher.quoteReplacement(rootBase));
        Matcher hm = UrlProxyServiceImpl.HEAD_TAG.matcher(rewritten);
        if (hm.find()) {
            rewritten = hm.replaceFirst(Matcher.quoteReplacement(
                    hm.group() + "<base href=\"" + baseHref + "\">"));
        } else {
            rewritten = "<base href=\"" + baseHref + "\">" + rewritten;
        }
        return rewritten;
    }

    private boolean looksLikeHtml(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 64), StandardCharsets.UTF_8)
                .trim().toLowerCase();
        return head.startsWith("<!doctype html") || head.startsWith("<html");
    }

    private boolean isInternalHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    /** 解析目标主机并拦截环回/内网/链路本地地址，防止 SSRF。 */
}
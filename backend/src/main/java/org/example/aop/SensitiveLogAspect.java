// 文件路径: backend/src/main/java/org/example/aop/SensitiveLogAspect.java
package org.example.aop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.SensitiveLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Aspect
@Component
@Order(60)
public class SensitiveLogAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveLogAspect.class);
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{2})\\w+(@\\w+\\.\\w+)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{4})\\d{10}(\\d{4})");
    private static final Pattern URL_SECRET_PATTERN = Pattern.compile("(key|token|secret|password|pwd)=([^&\\s]+)");

    @Around("@annotation(sensitiveLog)")
    public Object around(ProceedingJoinPoint joinPoint, SensitiveLog sensitiveLog) throws Throwable {
        String tag = sensitiveLog.logTag().isEmpty()
                ? joinPoint.getSignature().getDeclaringType().getSimpleName()
                  + "." + joinPoint.getSignature().getName()
                : sensitiveLog.logTag();

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        Set<String> maskSet = new HashSet<>(Arrays.asList(sensitiveLog.maskParams()));
        Set<String> keywordSet = new HashSet<>(Arrays.asList(sensitiveLog.maskKeywords()));

        StringBuilder sb = new StringBuilder();
        sb.append("【敏感日志】").append(tag).append(" 入参: ");

        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;

            if (args[i] == null) {
                sb.append(name).append("=null");
                continue;
            }
            if (args[i] instanceof HttpServletRequest) {
                sb.append(name).append("=<HttpRequest>");
                continue;
            }
            if (args[i] instanceof HttpServletResponse) {
                sb.append(name).append("=<HttpResponse>");
                continue;
            }

            String value = args[i].toString();
            if (value.length() > 500) {
                value = value.substring(0, 500) + "...(truncated)";
            }

            if (maskSet.contains(name)) {
                sb.append(name).append("=").append(maskAll(value));
            } else {
                boolean isKeywordMatch = keywordSet.stream()
                        .anyMatch(kw -> name.toLowerCase().contains(kw.toLowerCase()));
                if (isKeywordMatch) {
                    sb.append(name).append("=").append(maskAll(value));
                } else {
                    sb.append(name).append("=").append(maskSensitivePatterns(value));
                }
            }
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed(joinPoint.getArgs());
            long duration = System.currentTimeMillis() - start;
            sb.append(" | 耗时: ").append(duration).append("ms");
            if (result != null) {
                String resultStr = result.toString();
                sb.append(" | 返回: ").append(resultStr.length() > 200 ? resultStr.substring(0, 200) + "..." : resultStr);
            }
            log.info(sb.toString());
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            sb.append(" | 耗时: ").append(duration).append("ms | 异常: ").append(t.getMessage());
            log.warn(sb.toString());
            throw t;
        }
    }

    private String maskAll(String value) {
        if (value.length() <= 4) return "****";
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String maskSensitivePatterns(String value) {
        value = PHONE_PATTERN.matcher(value).replaceAll("$1****$2");
        value = EMAIL_PATTERN.matcher(value).replaceAll("$1***$2");
        value = ID_CARD_PATTERN.matcher(value).replaceAll("$1**********$2");
        value = URL_SECRET_PATTERN.matcher(value).replaceAll("$1=****");
        return value;
    }
}
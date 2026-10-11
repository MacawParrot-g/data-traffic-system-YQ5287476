// 文件路径: backend/src/main/java/org/example/aop/MethodCacheAspect.java
package org.example.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.MethodCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

@Aspect
@Component
@Order(30)
public class MethodCacheAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodCacheAspect.class);
    private static final String PREFIX = "method_cache:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(cache)")
    public Object around(ProceedingJoinPoint joinPoint, MethodCache cache) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String key = buildKey(joinPoint, cache);

        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("【缓存命中】{}", key);
            try {
                com.fasterxml.jackson.databind.JavaType javaType =
                        objectMapper.getTypeFactory().constructType(method.getGenericReturnType());
                return objectMapper.readValue(cached, javaType);
            } catch (Exception e) {
                log.warn("【缓存】反序列化失败，将重新执行方法: {}", e.getMessage());
                stringRedisTemplate.delete(key);
            }
        }

        Object result = joinPoint.proceed(joinPoint.getArgs());

        if (result != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(cache.ttl()));
                log.debug("【缓存写入】{} (TTL={}s)", key, cache.ttl());
            } catch (Exception e) {
                log.warn("【缓存】序列化失败: {}", e.getMessage());
            }
        }

        return result;
    }

    private String buildKey(ProceedingJoinPoint joinPoint, MethodCache cache) {
        StringJoiner sj = new StringJoiner(":");
        sj.add(PREFIX);

        if (!cache.prefix().isEmpty()) {
            sj.add(cache.prefix());
        } else {
            sj.add(joinPoint.getSignature().getDeclaringType().getSimpleName());
            sj.add(joinPoint.getSignature().getName());
        }

        if (!cache.key().isEmpty()) {
            EvaluationContext ctx = buildSpelContext(joinPoint);
            Object val = PARSER.parseExpression(cache.key()).getValue(ctx);
            sj.add(Objects.toString(val));
        } else {
            Object[] args = joinPoint.getArgs();
            sj.add(Arrays.deepToString(args));
        }

        return sj.toString();
    }

    private EvaluationContext buildSpelContext(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        return ctx;
    }
}
// 文件路径: backend/src/main/java/org/example/aop/IdempotentAspect.java
package org.example.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.Idempotent;
import org.example.common.BusinessException;
import org.example.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
@Order(20)
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String PREFIX = "idempotent:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = buildKey(joinPoint, idempotent);

        Boolean set = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.ttl()));
        if (!Boolean.TRUE.equals(set)) {
            log.warn("【幂等】重复请求被拦截: {}", key);
            throw new BusinessException(idempotent.message());
        }

        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } catch (Throwable t) {
            stringRedisTemplate.delete(key);
            throw t;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (!idempotent.key().isEmpty()) {
            EvaluationContext ctx = buildSpelContext(joinPoint);
            Object val = PARSER.parseExpression(idempotent.key()).getValue(ctx);
            return PREFIX + Objects.toString(val);
        }

        String username = UserContext.getUsername();
        if (username == null || username.isBlank()) username = "anonymous";
        return PREFIX + joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName() + ":" + username;
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
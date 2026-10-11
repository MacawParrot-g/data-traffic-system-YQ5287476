// 文件路径: backend/src/main/java/org/example/aop/DistributedLockAspect.java
package org.example.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.DistributedLock;
import org.example.common.BusinessException;
import org.example.common.UserContext;
import org.example.config.RedisDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Aspect
@Component
@Order(10)
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);
    private static final String PREFIX = "lock:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock lock) throws Throwable {
        String lockKey = buildKey(joinPoint, lock);
        String lockValue = UUID.randomUUID() + ":" + System.nanoTime();
        Duration expire = Duration.ofMillis(lock.unit().toMillis(lock.expire()));
        int retryTimes = lock.retryTimes();
        long retryWait = lock.retryWait();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        boolean acquired = false;
        for (int i = 0; i <= retryTimes; i++) {
            if (redisDistributedLock.tryLock(lockKey, lockValue, expire)) {
                acquired = true;
                break;
            }
            if (i < retryTimes) {
                log.debug("【分布式锁】{} 获取锁失败，第{}次重试，等待{}ms", lockKey, i + 1, retryWait);
                Thread.sleep(retryWait);
            }
        }

        if (!acquired) {
            log.warn("【分布式锁】{} 获取锁失败（已重试{}次），方法: {}", lockKey, retryTimes, fullMethodName);
            throw new BusinessException(lock.message());
        }

        log.debug("【分布式锁】获取锁成功: {}, 方法: {}", lockKey, fullMethodName);
        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } finally {
            redisDistributedLock.unlock(lockKey, lockValue);
            log.debug("【分布式锁】释放锁: {}", lockKey);
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, DistributedLock lock) {
        String keyExpr = lock.key();
        if (keyExpr.contains("#{")) {
            keyExpr = keyExpr.replace("#{", "").replace("}", "");
            EvaluationContext ctx = buildSpelContext(joinPoint);
            Object val = PARSER.parseExpression(keyExpr).getValue(ctx);
            return PREFIX + Objects.toString(val);
        }
        if (keyExpr.contains(":") || keyExpr.contains("#")) {
            EvaluationContext ctx = buildSpelContext(joinPoint);
            Object val = PARSER.parseExpression(keyExpr).getValue(ctx);
            return PREFIX + Objects.toString(val);
        }
        return PREFIX + keyExpr;
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
        String username = UserContext.getUsername();
        ctx.setVariable("username", username != null ? username : "anonymous");
        return ctx;
    }
}
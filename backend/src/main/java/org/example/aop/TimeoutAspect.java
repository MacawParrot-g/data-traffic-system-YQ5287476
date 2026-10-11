// 文件路径: backend/src/main/java/org/example/aop/TimeoutAspect.java
package org.example.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.annotation.Timeout;
import org.example.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Aspect
@Component
@Order(40)
public class TimeoutAspect {

    private static final Logger log = LoggerFactory.getLogger(TimeoutAspect.class);

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "timeout-worker");
        t.setDaemon(true);
        return t;
    });

    @Around("@annotation(timeout)")
    public Object around(ProceedingJoinPoint joinPoint, Timeout timeout) throws Throwable {
        String methodName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long timeoutMs = timeout.unit().toMillis(timeout.value());

        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                return joinPoint.proceed(joinPoint.getArgs());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }, executor);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("【超时】{} 执行超过{}{}被终止", methodName, timeout.value(), timeout.unit());
            throw new BusinessException(timeout.message() + "（" + timeout.value() + timeout.unit() + "）");
        }
    }
}
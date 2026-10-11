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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Aspect
@Component
@Order(40)
public class TimeoutAspect {

    private static final Logger log = LoggerFactory.getLogger(TimeoutAspect.class);

    @Around("@annotation(timeout)")
    public Object around(ProceedingJoinPoint joinPoint, Timeout timeout) throws Throwable {
        String methodName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long timeoutMs = timeout.unit().toMillis(timeout.value());

        final Thread workerThread = Thread.currentThread();
        final AtomicBoolean timedOut = new AtomicBoolean(false);

        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timeout-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.schedule(() -> {
            timedOut.set(true);
            workerThread.interrupt();
        }, timeoutMs, TimeUnit.MILLISECONDS);

        try {
            return joinPoint.proceed(joinPoint.getArgs());
        } catch (Throwable t) {
            if (timedOut.get()) {
                log.error("【超时】{} 执行超过{}{}被中断", methodName, timeout.value(), timeout.unit());
                throw new BusinessException(timeout.message() + "（" + timeout.value() + timeout.unit() + "）");
            }
            throw t;
        } finally {
            watchdog.shutdownNow();
            // 清理可能残留的中断标志，避免污染 Tomcat 请求线程
            Thread.interrupted();
        }
    }
}
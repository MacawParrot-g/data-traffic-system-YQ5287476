// 文件路径: backend/src/main/java/org/example/aop/RetryableAspect.java
package org.example.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.annotation.Retryable;
import org.example.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(50)
public class RetryableAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryableAspect.class);

    @Around("@annotation(retryable)")
    public Object around(ProceedingJoinPoint joinPoint, Retryable retryable) throws Throwable {
        String methodName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        int maxAttempts = retryable.maxAttempts();
        long delay = retryable.delay();
        double backoff = retryable.backoffMultiplier();

        Throwable lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Object result = joinPoint.proceed(joinPoint.getArgs());
                if (retryable.resultCheck() && result instanceof Result r && !r.isSuccess()) {
                    if (attempt < maxAttempts) {
                        log.warn("【重试】{} 第{}/{}次返回失败: {}，{}ms后重试",
                                methodName, attempt, maxAttempts, r.getMessage(), delay);
                        Thread.sleep(delay);
                        delay = (long) (delay * backoff);
                        continue;
                    }
                    log.error("【重试】{} 全部{}次尝试均返回失败结果", methodName, maxAttempts);
                }
                return result;
            } catch (Throwable t) {
                lastException = t;
                if (!shouldRetry(t, retryable)) {
                    throw t;
                }
                if (attempt < maxAttempts) {
                    log.warn("【重试】{} 第{}/{}次异常: {}，{}ms后重试",
                            methodName, attempt, maxAttempts, t.getMessage(), delay);
                    Thread.sleep(delay);
                    delay = (long) (delay * backoff);
                }
            }
        }
        log.error("【重试】{} 全部{}次尝试均失败，抛出最终异常", methodName, maxAttempts);
        throw lastException;
    }

    private boolean shouldRetry(Throwable t, Retryable retryable) {
        for (Class<? extends Throwable> noRetry : retryable.noRetryOn()) {
            if (noRetry.isInstance(t)) return false;
        }
        for (Class<? extends Throwable> retryOn : retryable.retryOn()) {
            if (retryOn.isInstance(t)) return true;
        }
        return false;
    }
}
// 文件路径: backend/src/main/java/org/example/annotation/Retryable.java
package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    int maxAttempts() default 3;

    long delay() default 1000;

    double backoffMultiplier() default 2.0;

    Class<? extends Throwable>[] retryOn() default { Exception.class };

    Class<? extends Throwable>[] noRetryOn() default {};

    boolean resultCheck() default false;
}
package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();

    long expire() default 30;

    TimeUnit unit() default TimeUnit.SECONDS;

    int retryTimes() default 3;

    long retryWait() default 200;

    String message() default "操作正在进行中，请稍后再试";
}
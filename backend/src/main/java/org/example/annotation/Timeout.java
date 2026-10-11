// 文件路径: backend/src/main/java/org/example/annotation/Timeout.java
package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timeout {

    long value() default 30;

    TimeUnit unit() default TimeUnit.SECONDS;

    String message() default "方法执行超时";
}
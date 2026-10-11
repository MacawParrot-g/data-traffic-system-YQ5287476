package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveLog {

    String[] maskParams() default {};

    String[] maskKeywords() default { "password", "pwd", "secret", "token", "key", "credential" };

    String logTag() default "";
}
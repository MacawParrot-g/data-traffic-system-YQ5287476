package org.example.config;

public class UserDataSourceContextHolder {

    private static final ThreadLocal<String> DS_KEY = new ThreadLocal<>();

    public static void set(String key) {
        DS_KEY.set(key);
    }

    public static String get() {
        return DS_KEY.get();
    }

    public static void clear() {
        DS_KEY.remove();
    }
}

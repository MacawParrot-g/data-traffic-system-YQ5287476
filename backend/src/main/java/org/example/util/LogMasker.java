package org.example.util;

import java.util.regex.Pattern;

public final class LogMasker {
    private static final Pattern PHONE = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    private static final Pattern EMAIL = Pattern.compile("(\\w{2})\\w+(@\\w+\\.\\w+)");
    private static final Pattern ID_CARD = Pattern.compile("(\\d{4})\\d{10}(\\d{4})");
    private static final Pattern URL_SECRET = Pattern.compile("(key|token|secret|password|pwd)=([^&\\s]+)");
    private static final Pattern KV_SECRET = Pattern.compile(
            "(password|pwd|secret|token|credential|newPwd)\\s*[=:]\\s*([^,\\s}\\]]+)",
            Pattern.CASE_INSENSITIVE);

    private LogMasker() {}

    public static String mask(String raw) {
        if (raw == null) return null;
        String v = PHONE.matcher(raw).replaceAll("$1****$2");
        v = EMAIL.matcher(v).replaceAll("$1***$2");
        v = ID_CARD.matcher(v).replaceAll("$1**********$2");
        v = URL_SECRET.matcher(v).replaceAll("$1=****");
        v = KV_SECRET.matcher(v).replaceAll("$1=****");
        return v;
    }
}

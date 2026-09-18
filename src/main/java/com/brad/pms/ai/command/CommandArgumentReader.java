package com.brad.pms.ai.command;

import com.brad.pms.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.Set;
import java.util.Map;

public final class CommandArgumentReader {

    private CommandArgumentReader() {
    }

    public static Long requiredLong(Map<String, Object> arguments, String key) {
        Long value = longValue(arguments.get(key));
        if (value == null || value < 1) throw BusinessException.error("参数 " + key + " 必须是正整数");
        return value;
    }

    public static Long optionalLong(Map<String, Object> arguments, String key) {
        return longValue(arguments.get(key));
    }

    public static Integer optionalInteger(Map<String, Object> arguments, String key, Integer defaultValue) {
        Object raw = arguments.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number number) return number.intValue();
        if (raw instanceof String string) {
            try { return Integer.valueOf(string); } catch (NumberFormatException ignored) { }
        }
        throw BusinessException.error("参数 " + key + " 必须是整数");
    }

    public static String requiredText(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        if (!(raw instanceof String value) || value.isBlank()) {
            throw BusinessException.error("参数 " + key + " 不能为空");
        }
        return value.trim();
    }

    public static String optionalText(Map<String, Object> arguments, String key) {
        Object raw = arguments.get(key);
        return raw instanceof String value && !value.isBlank() ? value.trim() : null;
    }

    public static LocalDate optionalDate(Map<String, Object> arguments, String key) {
        String value = optionalText(arguments, key);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw BusinessException.error("参数 " + key + " 必须是 yyyy-MM-dd 日期");
        }
    }

    public static void rejectUnknown(Map<String, Object> arguments, Set<String> allowed, String command) {
        arguments.keySet().stream()
                .filter(key -> !allowed.contains(key))
                .findFirst()
                .ifPresent(key -> { throw BusinessException.error("不支持的 " + command + " 参数: " + key); });
    }

    private static Long longValue(Object raw) {
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String string) {
            try { return Long.valueOf(string); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
}

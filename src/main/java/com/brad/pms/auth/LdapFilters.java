package com.brad.pms.auth;

final class LdapFilters {

    private LdapFilters() {
    }

    static String escape(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char item = value.charAt(i);
            switch (item) {
                case '\\' -> result.append("\\5c");
                case '*' -> result.append("\\2a");
                case '(' -> result.append("\\28");
                case ')' -> result.append("\\29");
                case '\0' -> result.append("\\00");
                default -> result.append(item);
            }
        }
        return result.toString();
    }

    static String apply(String template, String identifier) {
        String pattern = template == null || template.isBlank() ? "(mail={0})" : template;
        return pattern.replace("{0}", escape(identifier));
    }
}

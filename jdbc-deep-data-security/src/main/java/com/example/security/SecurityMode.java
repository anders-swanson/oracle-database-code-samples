package com.example.security;

import java.util.Locale;

enum SecurityMode {
    AUTO,
    COMPAT,
    DEEPSEC;

    static SecurityMode parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).startsWith("--mode=")
                ? value.substring("--mode=".length())
                : value;
        return SecurityMode.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
    }
}

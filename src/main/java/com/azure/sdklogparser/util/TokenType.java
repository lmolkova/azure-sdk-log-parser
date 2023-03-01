package com.azure.sdklogparser.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum TokenType {
    DATE("date"),
    TIME("time"),
    TIMESTAMP("timestamp"),
    MESSAGE("message"),
    LOG_LEVEL("level"),
    CLASS("class"),
    LOGGER("logger"),
    THREAD("thread"),
    LINE("line");

    private static final Map<String, TokenType> TOKEN_TYPE_MAP = Arrays.stream(TokenType.values())
            .collect(HashMap::new, (existing, value) -> existing.put(value.getValue(), value),
                    (a, b) -> a.putAll(b));
    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TokenType fromString(String value) {
        if (value == null) {
            return null;
        }

        return TOKEN_TYPE_MAP.get(value.toLowerCase(Locale.ROOT));
    }
}

package com.azure.sdklogparser.util;

import com.microsoft.applicationinsights.telemetry.Telemetry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Known types that can be mapped to properties in {@link Telemetry}.
 */
public enum TokenType {
    DATE("date", "Date of the log. Used in conjunction with 'time'."),
    TIME("time", "Time of the log. Used in conjunction with 'date'."),
    TIMESTAMP("timestamp", "Date and time of log. Mutually exclusive from 'date' and 'time'."),
    MESSAGE("message", "Log message."),
    LOG_LEVEL("level", "Log level."),
    LOGGER("logger", "Name of logger or class being logged."),
    THREAD("thread", "Name of thread."),
    LINE("line", "Line number.");

    private static final Map<String, TokenType> TOKEN_TYPE_MAP = Arrays.stream(TokenType.values())
            .collect(HashMap::new, (existing, value) -> existing.put(value.getValue(), value),
                    (a, b) -> a.putAll(b));
    private final String value;

    private final String description;

    TokenType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static TokenType fromString(String value) {
        if (value == null) {
            return null;
        }

        return TOKEN_TYPE_MAP.get(value.toLowerCase(Locale.ROOT));
    }
}

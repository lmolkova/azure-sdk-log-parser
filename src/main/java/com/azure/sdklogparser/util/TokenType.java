package com.azure.sdklogparser.util;

public enum TokenType {
    DEFAULT("default-text"),
    DATE("date"),
    TIME("time"),
    TIMESTAMP("timestamp"),
    MESSAGE("message"),
    LOG_LEVEL("level"),
    LOGGER_NAME("class"),
    THREAD("thread");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

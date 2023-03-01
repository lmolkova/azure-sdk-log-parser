package com.azure.sdklogparser.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Token {
    private static final Map<String, TokenType> TOKEN_TYPE_MAP;
    private final String name;
    private final String separator;
    private final TokenType tokenType;

    static {
        TOKEN_TYPE_MAP = Arrays.stream(TokenType.values()).collect(HashMap::new,
                (map, value) -> map.put(value.getValue(), value),
                (first, second) -> first.putAll(second));
    }

    public Token(String name, String separator) {
        this.name = name;
        this.separator = separator;
        this.tokenType = TOKEN_TYPE_MAP.getOrDefault(name, TokenType.DEFAULT);
    }

    public String getName() {
        return name;
    }

    public String getSeparator() {
        return separator;
    }

    public TokenType getTokenType() {
        return tokenType;
    }
}

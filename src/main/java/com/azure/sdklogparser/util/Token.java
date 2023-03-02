package com.azure.sdklogparser.util;

import com.azure.sdklogparser.PlaintextLogParserOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Describes a parameter in the {@link Layout}.
 *
 * @see PlaintextLogParserOptions
 * @see Layout
 */
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

        final String toLower = name.toLowerCase(Locale.ROOT);
        this.tokenType = TOKEN_TYPE_MAP.get(toLower);
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

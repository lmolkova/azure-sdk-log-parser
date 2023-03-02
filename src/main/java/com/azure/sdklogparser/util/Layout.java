package com.azure.sdklogparser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Layout {
    private static final Logger LOGGER = LoggerFactory.getLogger(Layout.class);

    public static final Layout DEFAULT = Layout.fromString("<date> <time> <level> <class> <thread> ");

    public static Layout fromString(String layoutStr) {
        layoutStr = layoutStr.replaceAll("\\s+", " ");
        var regex = Pattern.compile("<([\\w]+)>([^<]*)");
        var matcher = regex.matcher(layoutStr);

        final List<Token> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(1), matcher.group(2)));
        }

        // They didn't have any message token, so we assume it is at the end.
        if (!tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.MESSAGE)) {
            LOGGER.info("Did not find message token in layout. Assuming it is at the end.");
            tokens.add(new Token(TokenType.MESSAGE.getValue(), null));
        }

        return new Layout(tokens);
    }

    private final List<Token> tokens;
    private final String display;

    private Layout(List<Token> tokens) {
        this.tokens = Collections.unmodifiableList(tokens);

        this.display = tokens.stream()
                .map(t -> "<" + t.getName() + ">" + t.getSeparator())
                .collect(Collectors.joining(""));
    }

    public List<Token> getTokens() {
        return tokens;
    }

    @Override
    public String toString() {
        return display;
    }
}

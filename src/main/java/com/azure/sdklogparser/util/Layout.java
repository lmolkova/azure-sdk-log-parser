package com.azure.sdklogparser.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class Layout {
    public static class Token {
        private final String name;
        private final String separator;

        public Token(String name, String separator) {
            this.name = name;
            this.separator = separator;
        }

        public String getName() {
            return name;
        }

        public String getSeparator() {
            return separator;
        }
    }

    public static final Layout DEFAULT = Layout.fromString("<date> <time> <level> <class> <thread> ");
    public static Layout fromString(String layoutStr) {
        layoutStr = layoutStr.replaceAll("\\s+", " ");
        var regex = Pattern.compile("<([\\w]+)>([^<]*)");
        var matcher = regex.matcher(layoutStr);

        List<Token> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(1), matcher.group(2)));
        }

        return new Layout(tokens);
    }

    private final List<Token> tokens;
    private Layout(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Iterator<Token> getIterator() {
        return tokens.iterator();
    }
}

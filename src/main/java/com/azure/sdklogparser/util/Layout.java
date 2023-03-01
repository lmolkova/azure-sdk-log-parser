package com.azure.sdklogparser.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Layout {

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
    private final String display;

    private Layout(List<Token> tokens) {
        this.tokens = tokens;

        this.display = tokens.stream().map(t -> "<" + t.getName() + ">" + t.getSeparator()).collect(Collectors.joining(""));
    }

    public Iterator<Token> getIterator() {
        return tokens.iterator();
    }

    @Override
    public String toString() {
        return display;
    }
}

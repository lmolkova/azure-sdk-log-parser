package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.Token;
import com.azure.sdklogparser.util.TokenType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LayoutTests {

    /**
     * Parse a layout with message token.
     */
    @Test
    public void parseLayout() {
        // Arrange
        final String input = "[<timestamp>] <thread> (<logger>)  -  <customField>: <message>";
        final List<Token> expected = Arrays.asList(
                new Token(TokenType.TIMESTAMP.getValue(), "] "),
                new Token(TokenType.THREAD.getValue(), " ("),

                // Any extra whitespaces are removed.
                new Token(TokenType.LOGGER.getValue(), ") - "),
                new Token("customField", ": "),
                new Token(TokenType.MESSAGE.getValue(), "")
        );

        // Act
        final Layout layout = Layout.fromString(input);

        // Assert
        final List<Token> actual = layout.getTokens();
        assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            Token expectedToken = expected.get(i);
            Token actualToken = actual.get(i);

            assertEquals(expectedToken.getName(), actualToken.getName());
            assertEquals(expectedToken.getSeparator(), actualToken.getSeparator());
            assertEquals(expectedToken.getTokenType(), actualToken.getTokenType());
        }
    }

    /**
     * Parse a layout where message token is not added.
     */
    @Test
    public void parseLayoutNoMessageToken() {
        // Arrange
        final String input = "<line> <level> [<date> <time>]   <class>  -  :  ";
        final List<Token> expected = Arrays.asList(
                new Token(TokenType.LINE.getValue(), " "),
                new Token(TokenType.LOG_LEVEL.getValue(), " ["),
                new Token(TokenType.DATE.getValue(), " "),

                // Any extra whitespaces are removed.
                new Token(TokenType.TIME.getValue(), "] "),
                new Token("class", " - : "),
                new Token(TokenType.MESSAGE.getValue(), null)
        );

        // Act
        final Layout layout = Layout.fromString(input);

        // Assert
        final List<Token> actual = layout.getTokens();
        assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            Token expectedToken = expected.get(i);
            Token actualToken = actual.get(i);

            assertEquals(expectedToken.getName(), actualToken.getName());
            assertEquals(expectedToken.getSeparator(), actualToken.getSeparator());
            assertEquals(expectedToken.getTokenType(), actualToken.getTokenType());
        }
    }

    /**
     * Parse a layout with message token is somewhere in the middle.
     */
    @Test
    public void parseLayoutMessageTokenMiddle() {
        // Arrange
        final String input = "[<timestamp>] <thread> (<logger>) [<message>] <customField>";
        final List<Token> expected = Arrays.asList(
                new Token(TokenType.TIMESTAMP.getValue(), "] "),
                new Token(TokenType.THREAD.getValue(), " ("),

                // Any extra whitespaces are removed.
                new Token(TokenType.LOGGER.getValue(), ") ["),
                new Token(TokenType.MESSAGE.getValue(), "] "),
                new Token("customField", "")
        );

        // Act
        final Layout layout = Layout.fromString(input);

        // Assert
        final List<Token> actual = layout.getTokens();
        assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            Token expectedToken = expected.get(i);
            Token actualToken = actual.get(i);

            assertEquals(expectedToken.getName(), actualToken.getName());
            assertEquals(expectedToken.getSeparator(), actualToken.getSeparator());
            assertEquals(expectedToken.getTokenType(), actualToken.getTokenType());
        }
    }
}

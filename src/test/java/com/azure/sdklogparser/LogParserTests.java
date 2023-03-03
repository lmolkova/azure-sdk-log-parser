package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.TokenType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TelemetryContext;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static com.azure.sdklogparser.LogParser.ORIGINAL_MESSAGE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class LogParserTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MESSAGE = "onConnectionRemoteClosed";

    private final TelemetryContext telemetryContext = new TelemetryContext();
    private final Map<String, String> sdkMessageMap = new HashMap<>();

    private AutoCloseable autoCloseable;
    private RunInfo runInfo;
    private JsonLogParserOptions jsonLogParserOptions;
    private String sdkMessageJson;

    @Mock
    private TelemetryClient telemetryClient;

    @BeforeEach
    public void beforeEach() throws JsonProcessingException {
        autoCloseable = MockitoAnnotations.openMocks(this);

        when(telemetryClient.getContext()).thenReturn(telemetryContext);

        runInfo = new RunInfo("test-run-name", true, 10L);
        jsonLogParserOptions = new JsonLogParserOptions();

        sdkMessageMap.put("az.sdk.message", MESSAGE);
        sdkMessageMap.put("connectionId", "MF_2222_1111");
        sdkMessageMap.put("errorCondition", "amqp:link:detached");
        sdkMessageMap.put("errorDescription", "Connection closed.");
        sdkMessageMap.put("hostName", "demo.windows.net");

        sdkMessageJson = OBJECT_MAPPER.writeValueAsString(sdkMessageMap);
    }

    @AfterEach
    public void afterEach() throws Exception {
        if (autoCloseable != null) {
            autoCloseable.close();
        }
    }

    /**
     * Parses the sdk object and puts them in custom dimensions as well as sets text.
     */
    @Test
    public void parseSdkMessage() throws JsonProcessingException {
        // Arrange
        final TraceTelemetry telemetry = new TraceTelemetry();
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);

        // Act
        parser.parseSdkMessage(telemetry, sdkMessageJson);

        // Assert
        assertEquals(MESSAGE, telemetry.getMessage());

        final Map<String, String> actual = telemetry.getProperties();
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }

    /**
     * Tests that when there is trailing text in the message (possible due to mis-setting the message token), we try to
     * parse it again.
     */
    @Test
    public void parseSdkMessageTrailingText() throws JsonProcessingException {
        // Arrange
        final TraceTelemetry telemetry = new TraceTelemetry();
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);
        final String trashJson = "- - " + sdkMessageJson;

        // Act
        parser.parseSdkMessage(telemetry, trashJson);

        // Assert
        assertEquals(MESSAGE, telemetry.getMessage());

        final Map<String, String> actual = telemetry.getProperties();
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }

    /**
     * Tests that we don't throw anything when we unsuccessfully parse the message. (Possible that it is just a log
     * message about exceptions in the application.)
     */
    @Test
    public void parseSdkMessageInvalid() {
        // Arrange
        final TraceTelemetry telemetry = new TraceTelemetry();
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);
        final String invalid = "- - \tat com.azure.core.amqp.implementation.ExceptionUtil.toException(ExceptionUtil.java:85)";

        // Act
        assertThrows(JsonProcessingException.class, () -> parser.parseSdkMessage(telemetry, invalid));

        // Assert
        assertEquals(invalid, telemetry.getMessage());

        final Map<String, String> actual = telemetry.getProperties();
        assertTrue(actual.isEmpty());
    }

    @Test
    public void parsePlaintextDefaultLayout() {
        // Arrange
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);

        final long lineNumber = 10;
        final Layout layout = Layout.fromString("[<timestamp>] (<level>) (<thread>) <logger> <custom> ");
        final String message = "[2021-10-01 06:31:57,637] (WARN) (reactor-executor-1) c.a.m.ClientLogger customValue "
                + sdkMessageJson;

        // Act
        final TraceTelemetry actualTelemetry = parser.parseLine(message, lineNumber, layout);

        // Assert
        assertEquals(SeverityLevel.Warning, actualTelemetry.getSeverityLevel());
        assertEquals(MESSAGE, actualTelemetry.getMessage());

        // Create shallow object to remove references from.
        final Map<String, String> actual = new HashMap<>(actualTelemetry.getProperties());

        assertEquals(String.valueOf(lineNumber), actual.remove(TokenType.LINE.getValue()));

        // dryRun = true, so we have the original message
        assertEquals(message, actual.remove(ORIGINAL_MESSAGE_KEY));

        assertEquals("[2021-10-01 06:31:57,637", actual.remove(TokenType.TIMESTAMP.getValue()));;
        assertEquals("reactor-executor-1", actual.remove(TokenType.THREAD.getValue()));
        assertEquals("c.a.m.ClientLogger", actual.remove(TokenType.LOGGER.getValue()));
        assertEquals("customValue", actual.remove("custom"));

        // See what is left over.
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }
}

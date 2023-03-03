package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.TokenType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.extensibility.context.CloudContext;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TelemetryContext;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.azure.sdklogparser.LogParser.ORIGINAL_MESSAGE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogParserTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MESSAGE = "onConnectionRemoteClosed";
    private static final String UNIQUE_ID = "test-unique-id";
    private static final String RUN_ID = "test-run-name";

    private final TelemetryContext telemetryContext = new TelemetryContext();
    private final Map<String, String> sdkMessageMap = new HashMap<>();

    private AutoCloseable autoCloseable;
    private RunInfo runInfo;
    private JsonLogParserOptions jsonLogParserOptions;
    private String sdkMessageJson;

    @Mock
    private TelemetryClient telemetryClient;

    @Captor
    private ArgumentCaptor<TraceTelemetry> telemetryCaptor;

    @BeforeEach
    public void beforeEach() throws JsonProcessingException {
        autoCloseable = MockitoAnnotations.openMocks(this);

        when(telemetryClient.getContext()).thenReturn(telemetryContext);

        runInfo = new RunInfo(RUN_ID, true, 10L, UNIQUE_ID);
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

    @Test
    public void cloudInformationSet() {
        // Act
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);

        // Assert
        verify(telemetryClient).getContext();

        final CloudContext cloudContext = telemetryContext.getCloud();
        assertEquals(RUN_ID, cloudContext.getRole());
        assertEquals(UNIQUE_ID, cloudContext.getRoleInstance());
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

    /**
     * Tests that it can parse a line with timestamp specified and since dry-run is false, does not have
     * "original-line".
     */
    @Test
    public void parsePlaintextLayoutTimestamp() {
        // Arrange
        final RunInfo nonDryRun = new RunInfo("foo-bar", false, 15L, UNIQUE_ID);
        final LogParser parser = new LogParser(telemetryClient, nonDryRun, jsonLogParserOptions);

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

        assertEquals("[2021-10-01 06:31:57,637", actual.remove(TokenType.TIMESTAMP.getValue()));
        ;
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

    /**
     * Tests that it can parse a line with date and time layout.
     */
    @Test
    public void parsePlaintextLayout() {
        // Arrange
        final LogParser parser = new LogParser(telemetryClient, runInfo, jsonLogParserOptions);

        final long lineNumber = 10;
        final Layout layout = Layout.fromString("[<date> - <time>] (<level>) <logger>: <message>");
        final String message = "[2022-12-10 - 06:31:10] (ERROR) c.a.m.ClientLogger: " + sdkMessageJson;

        // Act
        final TraceTelemetry actualTelemetry = parser.parseLine(message, lineNumber, layout);

        // Assert
        assertEquals(SeverityLevel.Error, actualTelemetry.getSeverityLevel());
        assertEquals(MESSAGE, actualTelemetry.getMessage());

        // Create shallow object to remove references from.
        final Map<String, String> actual = new HashMap<>(actualTelemetry.getProperties());

        assertEquals(String.valueOf(lineNumber), actual.remove(TokenType.LINE.getValue()));

        // dryRun = true, so we have the original message
        assertEquals(message, actual.remove(ORIGINAL_MESSAGE_KEY));

        assertEquals("[2022-12-10 06:31:10", actual.remove(TokenType.TIMESTAMP.getValue()));
        ;
        assertEquals("c.a.m.ClientLogger", actual.remove(TokenType.LOGGER.getValue()));

        // See what is left over.
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }

    /**
     * Parses a plaintext log file and outputs Telemetry as expected.
     */
    @Test
    public void parsePlaintextLogFile() throws IOException {
        // Arrange
        final RunInfo plainTextRunInfo = new RunInfo("my-run-name", false, 100L, "my-unique-id");
        final LogParser parser = new LogParser(telemetryClient, plainTextRunInfo, jsonLogParserOptions);
        // 2023-01-10 11:30:23.701  INFO 8001 --- [main] c.a.m.s.ServiceBusClientBuilder: # of open clients with shared connection: 1
        final Layout layout = Layout.fromString("<date> <time>  <level> <pid> --- [<thread>] <logger>          : <message>");

        // Act
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("plaintext.log");
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            parser.parse(inputStreamReader, layout, false);
        }

        // Assert
        verify(telemetryClient, atLeastOnce()).trackTrace(telemetryCaptor.capture());
        verify(telemetryClient).flush();

        final List<TraceTelemetry> allValues = telemetryCaptor.getAllValues();

        assertEquals(28, allValues.size());
    }

    /**
     * Parse JSON with default token type names defined in {@link TokenType}.
     */
    @Test
    public void parseJson() throws JsonProcessingException {
        // Arrange
        final RunInfo nonDryRun = new RunInfo("foo-bar", false, 15L, UNIQUE_ID);

        // Default JsonLogParserOptions used.
        final LogParser parser = new LogParser(telemetryClient, nonDryRun, jsonLogParserOptions);
        final String instantRepresentation = Instant.ofEpochMilli(1677869692082L).toString();

        final long lineNumber = 15;

        final TestLogLine line = new TestLogLine();
        line.setLogger("com.azure.messaging.eventhubs.PartitionPumpManager");
        line.setTimestamp(instantRepresentation);
        line.setThread("reactor-executor-1");
        line.setLevel(Level.ERROR);
        line.setMessage(sdkMessageJson);

        final String lineSerialized = OBJECT_MAPPER.writeValueAsString(line);

        // Act
        final TraceTelemetry actualTelemetry = parser.parseLine(lineSerialized, lineNumber, jsonLogParserOptions);

        // Assert
        assertEquals(SeverityLevel.Error, actualTelemetry.getSeverityLevel());
        assertEquals(MESSAGE, actualTelemetry.getMessage());

        // Create shallow object to remove references from.
        final Map<String, String> actual = new HashMap<>(actualTelemetry.getProperties());

        assertEquals(String.valueOf(lineNumber), actual.remove(TokenType.LINE.getValue()));

        assertEquals(instantRepresentation, actual.remove(TokenType.TIMESTAMP.getValue()));
        ;
        assertEquals("reactor-executor-1", actual.remove(TokenType.THREAD.getValue()));
        assertEquals("com.azure.messaging.eventhubs.PartitionPumpManager",
                actual.remove(TokenType.LOGGER.getValue()));

        // See what is left over.
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }

    /**
     * Parse JSON with default token type names defined in {@link TokenType}.
     */
    @Test
    public void parseJsonCustomFieldNames() throws JsonProcessingException {
        // Arrange
        final RunInfo nonDryRun = new RunInfo("foo-bar", true, 15L, UNIQUE_ID);

        jsonLogParserOptions.setIsDryRun(true);
        jsonLogParserOptions.setMessageKey(CustomLogLine.MESSAGE_KEY);
        jsonLogParserOptions.setTimestamp(CustomLogLine.TIMESTAMP_KEY);
        jsonLogParserOptions.setThread(CustomLogLine.THREAD_KEY);
        jsonLogParserOptions.setLogger(CustomLogLine.LOGGER_KEY);
        jsonLogParserOptions.setLogLevel(CustomLogLine.LEVEL_KEY);

        // Default JsonLogParserOptions used.
        final LogParser parser = new LogParser(telemetryClient, nonDryRun, jsonLogParserOptions);
        final String instantRepresentation = Instant.ofEpochMilli(1677869692082L).toString();

        final long lineNumber = 15;

        final CustomLogLine line = new CustomLogLine();
        line.setLogger("com.azure.messaging.eventhubs.PartitionPumpManager");
        line.setTimestamp(instantRepresentation);
        line.setThread("reactor-executor-1");
        line.setLevel(Level.ERROR);
        line.setMessage(sdkMessageJson);

        final String lineSerialized = OBJECT_MAPPER.writeValueAsString(line);

        // Act
        final TraceTelemetry actualTelemetry = parser.parseLine(lineSerialized, lineNumber, jsonLogParserOptions);

        // Assert
        assertEquals(SeverityLevel.Error, actualTelemetry.getSeverityLevel());
        assertEquals(MESSAGE, actualTelemetry.getMessage());

        // Create shallow object to remove references from.
        final Map<String, String> actual = new HashMap<>(actualTelemetry.getProperties());

        assertEquals(String.valueOf(lineNumber), actual.remove(TokenType.LINE.getValue()));

        assertEquals(instantRepresentation, actual.remove(TokenType.TIMESTAMP.getValue()));
        assertEquals("reactor-executor-1", actual.remove(TokenType.THREAD.getValue()));
        assertEquals("com.azure.messaging.eventhubs.PartitionPumpManager",
                actual.remove(TokenType.LOGGER.getValue()));

        // See what is left over.
        assertEquals(sdkMessageMap.size(), actual.size());

        sdkMessageMap.forEach((expectedKey, expectedValue) -> {
            assertTrue(actual.containsKey(expectedKey));

            final String actualValue = actual.get(expectedKey);
            assertEquals(expectedValue, actualValue);
        });
    }

    /**
     * A possible user log line. Names of fields default from {@link TokenType}.
     */
    private static final class TestLogLine {
        private String logger;
        private String timestamp;
        private Level level;
        private String thread;

        private String message;

        public String getLogger() {
            return logger;
        }

        public void setLogger(String logger) {
            this.logger = logger;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Level getLevel() {
            return level;
        }

        public void setLevel(Level level) {
            this.level = level;
        }

        public String getThread() {
            return thread;
        }

        public void setThread(String thread) {
            this.thread = thread;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * A possible user log line with custom field names.
     */
    private static final class CustomLogLine {
        static final String LOGGER_KEY = "test-logger";
        static final String TIMESTAMP_KEY = "test-timestamp";
        static final String LEVEL_KEY = "test-log-level";
        static final String THREAD_KEY = "test-thread";
        static final String MESSAGE_KEY = "test-message";

        @JsonProperty(LOGGER_KEY)
        private String logger;
        @JsonProperty(TIMESTAMP_KEY)
        private String timestamp;
        @JsonProperty(LEVEL_KEY)
        private Level level;
        @JsonProperty(THREAD_KEY)
        private String thread;

        @JsonProperty(MESSAGE_KEY)
        private String message;

        public String getLogger() {
            return logger;
        }

        public void setLogger(String logger) {
            this.logger = logger;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Level getLevel() {
            return level;
        }

        public void setLevel(Level level) {
            this.level = level;
        }

        public String getThread() {
            return thread;
        }

        public void setThread(String thread) {
            this.thread = thread;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

}

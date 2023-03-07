package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.TokenType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.azure.sdklogparser.LogParser.ORIGINAL_MESSAGE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        final TestLogLine first = new TestLogLine().setTimestamp("2023-01-10 11:30:24.459").setLevel(Level.INFO)
                .setLogger("c.a.c.a.i.handler.ConnectionHandler").setThread("ctor-executor-1")
                .setMessage("onConnectionRemoteOpen");
        first.sdkMessageDetails.put("az.sdk.message", "onConnectionRemoteOpen");
        first.sdkMessageDetails.put("connectionId", "MF_8a_16");
        first.sdkMessageDetails.put("hostName", "test-application.servicebus.windows.net");
        first.sdkMessageDetails.put("remoteContainer", "9de_G28");
        final TestLogLine second = new TestLogLine().setTimestamp("2023-01-10 11:30:24.493").setLevel(Level.WARN)
                .setLogger("c.a.c.a.i.handler.SessionHandler").setThread("ctor-executor-3")
                .setMessage("onSessionRemoteOpen");
        second.sdkMessageDetails.put("az.sdk.message", "onSessionRemoteOpen");
        second.sdkMessageDetails.put("connectionId", "MF_8a_16");
        second.sdkMessageDetails.put("sessionName", "test-queue-session");
        second.sdkMessageDetails.put("sessionIncCapacity", "0");
        second.sdkMessageDetails.put("sessionOutgoingWindow", "2147483647");

        // Test the first few lines then the last one after the exception.
        final List<TestLogLine> expectedLines = Arrays.asList(
                new TestLogLine().setTimestamp("2023-01-10 11:30:23.084").setLevel(Level.INFO)
                        .setLogger("bus.TestApplication").setThread("main")
                        .setMessage("Starting TestApplication using Java 17.0.2"),
                new TestLogLine().setTimestamp("2023-01-10 11:30:23.085").setLevel(Level.INFO)
                        .setLogger("bus.TestApplication").setThread("main")
                        .setMessage("No active profile set, falling back to 1 default profile: \"default\""),
                new TestLogLine().setTimestamp("2023-01-10 11:30:23.701").setLevel(Level.INFO)
                        .setLogger("c.a.m.s.ServiceBusClientBuilder").setThread("main")
                        .setMessage("# of open clients with shared connection: 1"),
                first, second);

        // Last line after all the exceptions.
        final TestLogLine last = new TestLogLine().setTimestamp("2022-11-14 10:45:09.286").setLevel(Level.ERROR)
                .setLogger("c.a.m.s.i.ServiceBusConnectionProcessor").setThread("ctor-executor-1")
                .setMessage("Transient error occurred. Retrying.");
        last.sdkMessageDetails.put("az.sdk.message", "Transient error occurred. Retrying.");
        last.sdkMessageDetails.put("exception", "connection aborted, errorContext[NAMESPACE: test-application.servicebus.windows.net. ERROR CONTEXT: N/A]");
        last.sdkMessageDetails.put("entityPath", "N/A");
        last.sdkMessageDetails.put("tryCount", "0");
        last.sdkMessageDetails.put("interval_ms", "4511");

        // Act
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("plaintext.log");

        assertNotNull(inputStream);

        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            parser.parse(inputStreamReader, layout, false);
        }

        // Assert
        verify(telemetryClient, atLeastOnce()).trackTrace(telemetryCaptor.capture());
        verify(telemetryClient).flush();

        final List<TraceTelemetry> allValues = telemetryCaptor.getAllValues();

        assertEquals(10, allValues.size());

        for (int i = 0; i < expectedLines.size(); i++) {
            final TestLogLine expected = expectedLines.get(i);
            final TraceTelemetry actual = allValues.get(i);

            assertLogLine(expected, actual);
        }

        // Assert last log line after the exceptions.
        final TraceTelemetry lastActual = allValues.get(allValues.size() - 1);
        assertLogLine(last, lastActual);
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
     * Parse a json log.
     */
    @Test
    public void parseJsonLog() throws IOException {
        // Arrange
        // Timestamp field is found in the "datetime" json property and similar for the message.
        jsonLogParserOptions.setTimestamp("datetime");
        jsonLogParserOptions.setMessageKey("msg");

        final RunInfo run = new RunInfo("my-run-name", false, 100L, "my-unique-id");
        final LogParser parser = new LogParser(telemetryClient, run, jsonLogParserOptions);

        final TestLogLine first = new TestLogLine().setTimestamp("2022-12-01T10:16:12.001Z").setLevel(Level.INFO)
                .setLogger("com.foo.BarLogger").setThread("partition-pump-1")
                .setMessage("Customer log message");
        final TestLogLine second = new TestLogLine().setTimestamp("2022-12-01T10:22:02.038Z").setLevel(Level.WARN)
                .setLogger("c.a.c.a.i.RequestResponseChannel").setThread("reactor-executor-198")
                .setMessage("Error in SendLinkHandler. Disposing unconfirmed sends.");
        second.sdkMessageDetails.put("az.sdk.message", "Error in SendLinkHandler. Disposing unconfirmed sends.");
        second.sdkMessageDetails.put("exception", "The connection was inactive for more than the allowed 300000 milliseconds and is closed by container 'LinkTracker'. TrackingId:f_G7, SystemTracker:gateway5, Timestamp:2022-12-01T10:22:02, errorContext[NAMESPACE: contoso.com. ERROR CONTEXT: N/A, PATH: $cbs, REFERENCE_ID: cbs:sender, LINK_CREDIT: 12]");
        second.sdkMessageDetails.put("connectionId", "MF_0b9a58_1674924907030");
        second.sdkMessageDetails.put("linkName", "cbs");
        final TestLogLine third = new TestLogLine().setTimestamp("2022-12-01T10:53:19.093Z").setLevel(Level.ERROR)
                .setLogger("reactor.core.primitive.Operators").setThread("parallel-3")
                .setMessage("Operator called default onErrorDropped");

        final List<TestLogLine> expectedLines = Arrays.asList(first, second, third);

        // Act
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("json.log");

        assertNotNull(inputStream);

        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            parser.parse(inputStreamReader, Layout.DEFAULT, true);
        }

        // Assert
        verify(telemetryClient, atLeastOnce()).trackTrace(telemetryCaptor.capture());
        verify(telemetryClient).flush();

        final List<TraceTelemetry> allValues = telemetryCaptor.getAllValues();
        assertEquals(expectedLines.size(), allValues.size());

        for (int i = 0; i < expectedLines.size(); i++) {
            final TestLogLine expected = expectedLines.get(i);
            final TraceTelemetry actual = allValues.get(i);

            assertLogLine(expected, actual);
        }
    }

    private static void assertSeverityLevel(Level level, SeverityLevel actual) {
        switch (level) {
            case ERROR:
                assertEquals(SeverityLevel.Error, actual);
                return;
            case INFO:
                assertEquals(SeverityLevel.Information, actual);
                return;
            case WARN:
                assertEquals(SeverityLevel.Warning, actual);
                return;
            case DEBUG:
            case TRACE:
            default:
                assertEquals(SeverityLevel.Verbose, actual);
        }
    }

    private static void assertLogLine(TestLogLine expected, TraceTelemetry actual) {
        assertEquals(expected.getMessage(), actual.getMessage());
        assertSeverityLevel(expected.getLevel(), actual.getSeverityLevel());

        assertEquals(expected.getLogger(), actual.getProperties().get(TokenType.LOGGER.getValue()));
        assertEquals(expected.getThread(), actual.getProperties().get(TokenType.THREAD.getValue()));
        assertEquals(expected.getTimestamp(), actual.getProperties().get(TokenType.TIMESTAMP.getValue()));

        expected.sdkMessageDetails.forEach((key, expectedValue) -> {
            assertTrue(actual.getProperties().containsKey(key), "Did not contain key: " + key);
            assertEquals(expectedValue, actual.getProperties().get(key));
        });
    }

    /**
     * A possible user log line. Names of fields default from {@link TokenType}.
     */
    private static final class TestLogLine {
        @JsonProperty
        private String logger;
        @JsonProperty
        private String timestamp;
        @JsonProperty
        private Level level;
        @JsonProperty
        private String thread;
        @JsonProperty
        private String message;

        @JsonIgnore
        private final Map<String, String> sdkMessageDetails = new HashMap<>();

        public String getLogger() {
            return logger;
        }

        public TestLogLine setLogger(String logger) {
            this.logger = logger;
            return this;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public TestLogLine setTimestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Level getLevel() {
            return level;
        }

        public TestLogLine setLevel(Level level) {
            this.level = level;
            return this;
        }

        public String getThread() {
            return thread;
        }

        public TestLogLine setThread(String thread) {
            this.thread = thread;
            return this;
        }

        public String getMessage() {
            return message;
        }

        public TestLogLine setMessage(String message) {
            this.message = message;
            return this;
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

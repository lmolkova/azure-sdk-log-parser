package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.Token;
import com.azure.sdklogparser.util.TokenType;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.extensibility.context.CloudContext;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<HashMap<String, Object>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final Pattern MULTIPLE_WHITESPACE_PATTERN = Pattern.compile(" {2,}");
    /**
     * Default value if the value for a key is null (i.e. errorDescription).
     */
    private static final String NULL = "null";
    /**
     * The fields to remap to known values.  Message and log level are skipped because they are set in other fields on
     * The telemetry data.
     */
    private static final List<TokenType> TOKEN_TYPES_TO_REMAP = Arrays.stream(TokenType.values())
            .filter(t -> t != TokenType.MESSAGE && t != TokenType.LOG_LEVEL)
            .collect(Collectors.toList());

    static final String AZ_SDK_MESSAGE_KEY = "az.sdk.message";
    static final String ORIGINAL_MESSAGE_KEY = "original-message";

    private final TelemetryClient telemetryClient;
    private final JsonLogParserOptions jsonLogParserOptions;
    private final RunInfo runInfo;

    public LogParser(TelemetryClient telemetryClient, RunInfo runInfo, JsonLogParserOptions jsonLogParserOptions) {
        this.telemetryClient = telemetryClient;
        this.jsonLogParserOptions = jsonLogParserOptions;

        final CloudContext cloudContext = telemetryClient.getContext().getCloud();
        cloudContext.setRole(runInfo.getRunName());
        cloudContext.setRoleInstance(runInfo.getUniqueId());
        this.runInfo = runInfo;
    }

    public void parse(InputStreamReader text, Layout layout, boolean isJson) throws IOException {
        long fileLineNumber = 0;
        try (BufferedReader br = new BufferedReader(text)) {
            String line;
            while (runInfo.shouldKeepGoing() && (line = br.readLine()) != null) {
                processLine(isJson, line, fileLineNumber, layout);
                fileLineNumber++;
            }

            if (fileLineNumber == 0) {
                LOGGER.error("File is empty.");
            }
        } finally {
            telemetryClient.flush();
        }
    }

    void processLine(boolean isJson, String prevLine, long fileLineNumber, Layout layout) {
        TraceTelemetry telemetry = null;
        try {
            if (isJson) {
                telemetry = parseLine(prevLine, fileLineNumber, jsonLogParserOptions);
            } else {
                telemetry = parseLine(prevLine, fileLineNumber, layout);
            }
        } finally {
            if (telemetry != null) {
                telemetryClient.trackTrace(telemetry);
                runInfo.nextRecord(telemetry);
            }
        }
    }

    TraceTelemetry parseLine(String line, long fileLineNumber, JsonLogParserOptions options) {
        final TraceTelemetry telemetry = new TraceTelemetry();
        telemetry.getProperties().put(TokenType.LINE.getValue(), String.valueOf(fileLineNumber));

        final LogLine log;
        try {
            log = OBJECT_MAPPER.readValue(line, LogLine.class);
        } catch (JsonProcessingException e) {
            LOGGER.info("Unable to parse log line. message[{}]", line, e);
            return telemetry;
        }

        final Map<String, Object> properties = log.getProperties();
        final Object o = properties.remove(options.getLogLevel());
        telemetry.setSeverityLevel(getSeverity(o != null ? o.toString() : null));

        final Object raw = properties.remove(options.getMessageKey());
        if (raw == null) {
            LOGGER.warn("Could not get the log's message. line[{}] key[{}] line[{}]", fileLineNumber,
                    options.getMessageKey(), line);

            telemetry.setMessage(line);
        } else {
            final String message = raw.toString();
            try {
                parseSdkMessage(telemetry, message);
            } catch (JsonProcessingException e) {
                LOGGER.info("Could not parse SDK message as JSON object. message[{}]", message);
            }
        }

        // Go through and remap the known parameters into consistent key names.
        remapParameter(TokenType.TIMESTAMP, options.getTimestamp(), properties);
        remapParameter(TokenType.LOGGER, options.getLogger(), properties);
        remapParameter(TokenType.THREAD, options.getThread(), properties);

        properties.forEach((key, value) -> telemetry.getProperties().put(key, value != null ? value.toString() : NULL));

        return telemetry;
    }

    TraceTelemetry parseLine(String line, long fileLineNumber, Layout layout) {
        final String replaced = MULTIPLE_WHITESPACE_PATTERN.matcher(line).replaceAll(" ");
        final TraceTelemetry telemetry = new TraceTelemetry();

        String dateStr = null;
        String timeStr = null;
        String timestampStr = null;
        int ind = 0;
        String sdkMessage = null;

        final List<Token> layoutTokens = layout.getTokens();
        final int lastIndex = layoutTokens.size() - 1;

        for (int i = 0; i < layoutTokens.size(); i++) {
            final Token next = layoutTokens.get(i);
            final boolean isLastToken = i == lastIndex;

            final int sepInd = isLastToken
                    ? replaced.length()
                    : replaced.indexOf(next.getSeparator(), ind);

            if (sepInd < 0) {
                LOGGER.error("LINE {}: can't find '{}' in '{}'", fileLineNumber, next.getName(), replaced);
                return null;
            }

            final String key = next.getName().trim();
            final String value = replaced.substring(ind, sepInd).trim();
            final TokenType tokenType = TokenType.fromString(key);

            if (tokenType == null) {
                telemetry.getProperties().putIfAbsent(key, value);
            } else {
                switch (tokenType) {
                    case DATE:
                        dateStr = value;
                        break;
                    case TIME:
                        timeStr = value;
                        break;
                    case TIMESTAMP:
                        timestampStr = value;
                    case LOG_LEVEL:
                        final SeverityLevel severityLevel = getSeverity(value);
                        telemetry.setSeverityLevel(severityLevel);
                        break;
                    case MESSAGE:
                        sdkMessage = value;
                        break;
                    default:
                        telemetry.getProperties().putIfAbsent(key, value);
                }
            }

            final int nextIndex = next.getSeparator() != null ? next.getSeparator().length() : 0;
            ind = sepInd + nextIndex;
        }

        if (runInfo.isDryRun() || LOGGER.isDebugEnabled()) {
            telemetry.getProperties().put(ORIGINAL_MESSAGE_KEY, line);
        }

        // LogAnalytics/AppInsights doesn't like timestamps in the past, so we'll put them in the custom dimension
        final Map<String, String> customProperties = telemetry.getProperties();

        // If they didn't have a timestamp field in their layout, we'll create one from the combination of date
        // and time.
        if (timestampStr == null) {
            timestampStr = dateStr + " " + timeStr;
        }
        customProperties.put(TokenType.TIMESTAMP.getValue(), timestampStr);
        customProperties.put(TokenType.LINE.getValue(), String.valueOf(fileLineNumber));

        try {
            parseSdkMessage(telemetry, sdkMessage);
        } catch (JsonProcessingException e) {
            LOGGER.info("Could not parse SDK message as JSON object. message[{}]", sdkMessage);
        }

        return telemetry;
    }

    /**
     * Parses the SDK log message and updates the trace telemetry. If the message cannot be parsed, it is set as-is in
     * the {@link TraceTelemetry#getMessage() telemetry.getMessage()}.
     *
     * @param telemetry Telemetry to update.
     * @param message message to parse.
     *
     * @throws JsonProcessingException If it was unable to parse the message into a JSON object.
     */
    void parseSdkMessage(TraceTelemetry telemetry, String message) throws JsonProcessingException {
        HashMap<String, Object> properties;
        try {
            properties = OBJECT_MAPPER.readValue(message, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            // Possible that we missed some trailing characters when extracting the SDK message.
            int firstCurlyBrace = message.indexOf('{');

            if (firstCurlyBrace == -1) {
                telemetry.setMessage(message);
                throw e;
            }

            final String parsed = message.substring(firstCurlyBrace);

            properties = OBJECT_MAPPER.readValue(parsed, TYPE_REFERENCE);
        }

        if (properties == null) {
            LOGGER.info("Could not read SDK message using default. message[{}]", message);

            telemetry.setMessage(message);
            return;
        }

        properties.forEach((key, value) -> {
            final String finalValue = value == null ? NULL : value.toString();
            telemetry.getProperties().put(key, finalValue);
        });

        final Object value = properties.get(AZ_SDK_MESSAGE_KEY);

        telemetry.setMessage(value != null ? value.toString() : message);
    }

    private static void remapParameter(TokenType expectedKey, String actualKey, Map<String, Object> map) {
        if (expectedKey.getValue().equals(actualKey)) {
            return;
        }

        if (!map.containsKey(actualKey)) {
            return;
        }

        final Object removed = map.remove(actualKey);

        map.put(expectedKey.getValue(), removed);
    }

    private static SeverityLevel getSeverity(String value) {
        if (value == null) {
            return SeverityLevel.Verbose;
        }

        switch (value) {
            case "INFO":
                return SeverityLevel.Information;
            case "WARN":
                return SeverityLevel.Warning;
            case "ERROR":
                return SeverityLevel.Error;
            default:
                return SeverityLevel.Verbose;
        }
    }

    private static final class LogLine {
        private final Map<String, Object> properties = new HashMap<>();

        @JsonAnySetter
        void setProperty(String key, Object value) {
            properties.put(key, value);
        }

        Map<String, Object> getProperties() {
            return properties;
        }
    }
}

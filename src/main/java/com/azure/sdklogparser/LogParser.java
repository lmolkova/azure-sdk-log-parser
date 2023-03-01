package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.Token;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public class LogParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<HashMap<String, Object>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("[ ]{2,}");
    private static final String NULL = "null";

    private static final String AZ_SDK_MESSAGE_KEY = "az.sdk.message";

    private static final String[] DATE_FORMATS = new String[]{
            "^\\d{4}-\\d{2}-\\d{2}.*", // yyyy-MM-dd or yyyy-dd-MM
            "^\\d{4}/\\d{2}/\\d{2}.*", // yyyy/MM/dd or yyyy/dd/MM
            // ...
    };

    private final TelemetryClient telemetryClient;
    private final RunInfo runInfo;

    public LogParser(TelemetryClient telemetryClient, RunInfo runInfo) {
        this.telemetryClient = telemetryClient;

        final CloudContext cloudContext = telemetryClient.getContext().getCloud();
        cloudContext.setRole(runInfo.getRunName());
        cloudContext.setRoleInstance(runInfo.getUniqueId());
        this.runInfo = runInfo;
    }

    public void parse(InputStreamReader text, Layout layout) throws IOException {
        long fileLineNumber = 0;
        try (BufferedReader br = new BufferedReader(text)) {
            String prevLine = br.readLine();
            fileLineNumber++;
            if (prevLine == null) {
                LOGGER.error("File is empty.");
                return;
            }

            String line;
            while (runInfo.shouldKeepGoing() && (line = br.readLine()) != null) {
                // check if next line starts with date (otherwise append to current)
                while (line != null && !startsWithDate(line)) {
                    prevLine += line;
                    line = br.readLine();
                }

                TraceTelemetry telemetry = parseLine(prevLine, layout, fileLineNumber);
                runInfo.nextRecord(telemetry);

                prevLine = line;
                fileLineNumber++;
            }

            if (prevLine != null) {
                TraceTelemetry t = parseLine(prevLine, layout, fileLineNumber);
                runInfo.nextRecord(t);
            }
        } finally {
            telemetryClient.flush();
        }
    }

    private void parse(InputStreamReader reader, Layout layout, boolean isJson) throws IOException {
        if (isJson) {
            final Log log = OBJECT_MAPPER.readValue(reader, Log.class);
            System.out.println("foo");
        } else {
            parse(reader, layout);
        }
    }

    private boolean startsWithDate(String line) {
        for (var format : DATE_FORMATS) {
            if (Pattern.matches(format, line)) {
                return true;
            }
        }

        return false;
    }

    private TraceTelemetry parseLine(String line, Layout layout, long fileLineNumber) throws JsonProcessingException {
        final String replaced = MULTIPLE_WHITESPACE.matcher(line).replaceAll(" ");
        final TraceTelemetry telemetry = new TraceTelemetry();

        String dateStr = null;
        String timeStr = null;
        int ind = 0;

        final Iterator<Token> it = layout.getIterator();
        while (it.hasNext()) {
            final Token next = it.next();

            final int sepInd = replaced.indexOf(next.getSeparator(), ind);
            if (sepInd < 0) {
                LOGGER.error("LINE {}: can't find '{}' in '{}'", fileLineNumber, next.getName(), replaced);
                return null;
            }

            final String key = next.getName().trim();
            final String value = replaced.substring(ind, sepInd).trim();

            if (key.equals("level")) {
                final SeverityLevel severityLevel = getSeverity(value);
                telemetry.setSeverityLevel(severityLevel);
            } else if (key.equals("date")) {
                dateStr = value;
            } else if (key.equals("time")) {
                timeStr = value;
            } else {
                telemetry.getProperties().putIfAbsent(key, value);
            }

            ind = sepInd + next.getSeparator().length();
        }

        // Assuming SDK message is last index.
        final String sdkMessage = line.substring(ind);

        if (runInfo.isDryRun() || LOGGER.isDebugEnabled()) {
            telemetry.getProperties().put("original-message", line);
        }

        // LogAnalytics/AppInsights don't like timestamps in the past, so we'll put them on custom dimension
        final Map<String, String> customProperties = telemetry.getProperties();

        customProperties.put("timestamp", dateStr + " " + timeStr);
        customProperties.put("line", String.valueOf(fileLineNumber));

        try {
            final HashMap<String, Object> properties = parseSdkMessage(sdkMessage);
            properties.forEach((key, value) -> {
                final String finalValue = value == null ? NULL : value.toString();

                customProperties.put(key, finalValue);
            });

            final Object value = properties.getOrDefault(AZ_SDK_MESSAGE_KEY, sdkMessage);
            telemetry.setMessage(value != null ? value.toString() : sdkMessage);
        } catch (JsonProcessingException e) {
            LOGGER.info("Could not parse SDK message as JSON object. message:{}", sdkMessage, e);
            telemetry.setMessage(sdkMessage);
        } finally {
            telemetryClient.trackTrace(telemetry);
        }

        return telemetry;
    }

    private HashMap<String, Object> parseSdkMessage(String message)
            throws JsonProcessingException {
        try {
            return OBJECT_MAPPER.readValue(message, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            // Possible that we missed some trailing characters when extracting the SDK message.
            int firstCurlyBrace = message.indexOf('{');
            if (firstCurlyBrace == -1) {
                throw e;
            }

            final String parsed = message.substring(firstCurlyBrace);

            return OBJECT_MAPPER.readValue(parsed, TYPE_REFERENCE);
        }
    }

    private static SeverityLevel getSeverity(String value) {
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

    private static final class Log {
        JsonNode[] lines;
    }
}

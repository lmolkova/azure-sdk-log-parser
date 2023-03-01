package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.PrintTelemetryClient;
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
import java.util.regex.Pattern;

public class LogParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<HashMap<String, Object>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String NULL = "null";

    private static final String[] DATE_FORMATS = new String[]{
            "^\\d{4}-\\d{2}-\\d{2}.*", // yyyy-MM-dd or yyyy-dd-MM
            "^\\d{4}/\\d{2}/\\d{2}.*", // yyyy/MM/dd or yyyy/dd/MM
            // ...
    };

    private static final Pattern[] SDK_KVP_PATTERNS = new Pattern[]{
            Pattern.compile("([\\w\\d.]+)\\s*\\[([^]]+)]"),
            Pattern.compile("([\\w\\d.]+)\\s*:\\s*'([^']+)'")
    };

    private final Logger logger;
    private final TelemetryClient telemetryClient;
    private final RunInfo runInfo;

    public LogParser(String connectionString, RunInfo runInfo) {

        if (runInfo.isDryRun()) {
            this.telemetryClient = new PrintTelemetryClient();
        } else {
            this.telemetryClient = new TelemetryClient();
            this.telemetryClient.getContext().setConnectionString(connectionString);
        }

        CloudContext cloudContext = this.telemetryClient.getContext().getCloud();
        cloudContext.setRole(runInfo.getRunName());
        cloudContext.setRoleInstance(runInfo.getUniqueId());

        this.logger = LoggerFactory.getLogger(LogParser.class);
        this.runInfo = runInfo;
    }

    public void parse(InputStreamReader text, Layout layout) throws IOException {
        long fileLineNumber = 0;
        try (BufferedReader br = new BufferedReader(text)) {
            String prevLine = br.readLine();
            fileLineNumber++;
            if (prevLine == null) {
                logger.error("File is empty.");
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

    private TraceTelemetry parseLine(String line, Layout layout, long fileLineNumber) {
        if (line.contains("  ")) {
            line = line.replaceAll("\\s+", " ");
        }
        var logRecord = new TraceTelemetry();
        String dateStr = null;
        String timeStr = null;
        int ind = 0;
        var it = layout.getIterator();
        while (it.hasNext()) {
            Token next = it.next();

            int sepInd = line.indexOf(next.getSeparator(), ind);
            if (sepInd < 0) {
                logger.error("LINE {}: can't find '{}' in '{}'", fileLineNumber, next.getName(), line);
                return null;
            }

            String key = next.getName().trim();
            String value = line.substring(ind, sepInd).trim();

            if (key.equals("level")) {
                setSeverity(value, logRecord);
            } else if (key.equals("date")) {
                dateStr = value;
            } else if (key.equals("time")) {
                timeStr = value;
            } else {
                logRecord.getProperties().putIfAbsent(key, value);
            }
            ind = sepInd + next.getSeparator().length();
        }

        String sdkMessage = line.substring(ind);
        String sdkMessageNoContext = parseSdkMessage(sdkMessage, logRecord);
        logRecord.setMessage(sdkMessageNoContext);

        if (runInfo.isDryRun() || logger.isDebugEnabled()) {
            logRecord.getProperties().put("original-message", line);
        }

        // LogAnalytics/AppInsights don't like timestamps in the past, so we'll put them on custom dimension
        logRecord.getProperties().put("timestamp", dateStr + " " + timeStr);

        logRecord.getProperties().put("line", String.valueOf(fileLineNumber));

        try {
            final HashMap<String, Object> properties = OBJECT_MAPPER.readValue(sdkMessageNoContext, TYPE_REFERENCE);

            properties.forEach((key, value) -> {
                if (value == null) {
                    logRecord.getProperties().put(key, NULL);
                } else {
                    logRecord.getProperties().put(key, value.toString());
                }
            });
        } catch (JsonProcessingException e) {
            System.err.println("Exception parsing properties. Error: " + e);
        } finally {
            telemetryClient.trackTrace(logRecord);
        }

        return logRecord;
    }

    private String parseSdkMessage(String message, TraceTelemetry logRecord) {
        boolean[] removeFromMessage = new boolean[message.length()];

        for (var regex : SDK_KVP_PATTERNS) {
            var matches = regex.matcher(message);
            while (matches.find()) {
                String key = matches.group(1).trim();
                String value = matches.group(2).trim();
                if (key.equals("linkName")) {
                    int underscoreInd = value.indexOf('_');
                    if (underscoreInd > 0) {
                        logRecord.getProperties().putIfAbsent("partitionId", value.substring(0, underscoreInd));
                    }
                }
                logRecord.getProperties().putIfAbsent(key, value);
                for (int i = matches.start(); i < matches.end(); i++) {
                    removeFromMessage[i] = true;
                }
            }
        }

        var remainsBuilder = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            if (!removeFromMessage[i] && (message.charAt(i) != ' ' ||
                    i == 0 ||
                    remainsBuilder.length() == 0 ||
                    (message.charAt(i) == ' ' && remainsBuilder.charAt(remainsBuilder.length() - 1) != ' '))) {
                remainsBuilder.append(message.charAt(i));
            }
        }

        return remainsBuilder.toString().trim();
    }

    private static void setSeverity(String value, TraceTelemetry logRecord) {
        switch (value) {
            case "INFO":
                logRecord.setSeverityLevel(SeverityLevel.Information);
                break;
            case "WARN":
                logRecord.setSeverityLevel(SeverityLevel.Warning);
                break;
            case "ERROR":
                logRecord.setSeverityLevel(SeverityLevel.Error);
                break;
            default:
                logRecord.setSeverityLevel(SeverityLevel.Verbose);
                break;
        }
    }

    private static final class Log {
        JsonNode[] lines;
    }
}

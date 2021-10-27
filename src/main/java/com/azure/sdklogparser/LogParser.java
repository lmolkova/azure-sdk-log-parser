package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import com.azure.sdklogparser.util.RunInfoTelemetryInitializer;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class LogParser {
    private final static int DRY_RUN_MAX_LINES  = 2;
    private static final String[] DATE_FORMATS = new String[]{
            "^\\d{4}-\\d{2}-\\d{2}.*", // yyyy-MM-dd or yyyy-dd-MM
            "^\\d{4}/\\d{2}/\\d{2}.*", // yyyy/MM/dd or yyyy/dd/MM
            // ...
    };

    private static final Pattern[] SDK_KVP_PATTERNS = new Pattern[] {
            Pattern.compile("([\\w\\d\\.]+)\\s*\\[([^\\]]+)\\]"),
            Pattern.compile("([\\w\\d\\.]+)\\s*\\:{1}\\s*\\'([^\\']+)\\'")
    };

    private final Logger logger;
    private final TelemetryClient telemetryClient;
    private final RunInfo runInfo;
;
    public LogParser(String connectionString, RunInfo runInfo) {
        TelemetryConfiguration config = new TelemetryConfiguration();
        config.getTelemetryInitializers().add(new RunInfoTelemetryInitializer(runInfo));

        if (runInfo.isDryRun()) {
            config.setInstrumentationKey("unused");
        } else {
            config.setConnectionString(connectionString);
            config.setChannel(new InProcessTelemetryChannel(config));
        }

        this.telemetryClient = new TelemetryClient(config);
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

                parseLine(prevLine, layout, fileLineNumber);
                prevLine = line;
                fileLineNumber ++;
            }

            if (prevLine != null) {
                parseLine(prevLine, layout, fileLineNumber);
            }
        } finally {
            telemetryClient.flush();
        }
    }

    private boolean startsWithDate(String line) {
        for (var format : DATE_FORMATS){
            if (Pattern.matches(format, line)) {
                return true;
            }
        }

        return false;
    }

    private TraceTelemetry parseLine(String line, Layout layout, long fileLineNumber) {
        // We need a marker for Azure SDK message
        /*if (!line.contains("connectionId") && !line.contains("linkName")) {
            // not AMQP SDK log
            return null;
        }*/

        if (line.contains("  ")) {
            line = line.replaceAll("\\s+", " ");
        }
        var logRecord = new TraceTelemetry();
        String dateStr = null;
        String timeStr = null;
        int ind = 0;
        var it = layout.getIterator();
        while (it.hasNext()) {
            Layout.Token next = it.next();

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
        if (runInfo.isDryRun() || logger.isDebugEnabled()) {
            logRecord.getProperties().put("original-message", line);
        }

        logRecord.setMessage(String.format("Line  %d: %s", fileLineNumber, sdkMessageNoContext));

        // LogAnalytics/AppInsights don't like timestamps in the past, so we'll put them on custom dimension
        logRecord.getProperties().put("timestamp", dateStr + " " + timeStr);

        telemetryClient.track(logRecord);
        return logRecord;
    }

    private String parseSdkMessage(String message, TraceTelemetry logRecord)
    {
        boolean[] removeFromMessage = new boolean[message.length()];

        for( var regex : SDK_KVP_PATTERNS) {
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

        for (int i = 0; i < message.length(); i ++) {
            if (!removeFromMessage[i] && (message.charAt(i) != ' ' ||
                    i == 0 ||
                    remainsBuilder.length() == 0 ||
                    (message.charAt(i) == ' ' && remainsBuilder.charAt(remainsBuilder.length() - 1) != ' '))) {
                remainsBuilder.append(message.charAt(i));
            }
        }

        return remainsBuilder.toString().trim();
    }

    private void setSeverity(String value, TraceTelemetry logRecord){
        switch (value)
        {
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
}

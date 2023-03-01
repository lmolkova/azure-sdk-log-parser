package com.azure.sdklogparser.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RunInfo {
    private static final long MAX_DRY_RUN_LINES = 10;
    private final Logger logger = LoggerFactory.getLogger(RunInfo.class);
    private final ObjectMapper prettyPrinter = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final String runName;
    private final String uniqueId;
    private final boolean dryRun;
    private final long maxLines;

    private String minTimestamp = "2100-01-01T00:00:00";
    private String maxTimestamp = "1970-01-01T00:00:00";
    private final List<String> files = new ArrayList<>();
    private long linesRead = 0;
    private long linesReadInFile = 0;


    public RunInfo(String runName, boolean dryRun, long maxLines) {
        this.runName = runName;
        this.dryRun = dryRun;
        this.uniqueId = String.valueOf(Instant.now().getEpochSecond());
        this.maxLines = maxLines;
    }

    public String getRunName() {
        return runName;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean shouldKeepGoing() {
        return linesReadInFile < maxLines;
    }

    public void nextFile(String fileName) {
        System.out.printf("Reading file '%s'\n", fileName);
        linesReadInFile = 0;
        this.files.add(fileName);
    }

    public void nextRecord(Telemetry logRecord) {
        if (!(logRecord instanceof TraceTelemetry)) {
            return;
        }

        var timestamp = logRecord.getProperties().get("timestamp");
        if (timestamp.compareTo(minTimestamp) < 0)  {
            minTimestamp = timestamp;
        }

        if (timestamp.compareTo(maxTimestamp) > 0) {
            maxTimestamp = timestamp;
        }

        linesRead ++;
        linesReadInFile ++;
    }

    public void printRunSummary() {
        System.out.printf("----------------------\nParsed %d log records, min timestamp: '%s', max timestamp: %s\n", linesRead, minTimestamp, maxTimestamp);
        System.out.printf("Query all logs:\n" +
                "\ttraces | where cloud_RoleInstance  == \"%s\" and cloud_RoleName == \"%s\"\n", runName, uniqueId);

    }
}

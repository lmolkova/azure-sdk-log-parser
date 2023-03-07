package com.azure.sdklogparser.util;

import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;

import java.util.ArrayList;
import java.util.List;

public class RunInfo {
    private final String runName;
    private final String uniqueId;
    private final boolean dryRun;
    private final long maxLines;

    private String minTimestamp = "2100-01-01T00:00:00";
    private String maxTimestamp = "1970-01-01T00:00:00";
    private final List<String> files = new ArrayList<>();
    private long linesRead = 0;
    private long linesReadInFile = 0;

    public RunInfo(String runName, boolean dryRun, long maxLines, String uniqueId) {
        this.runName = runName;
        this.dryRun = dryRun;
        this.uniqueId = uniqueId;
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

        var timestamp = logRecord.getProperties().get(TokenType.TIMESTAMP.getValue());
        if (timestamp != null) {
            if (timestamp.compareTo(minTimestamp) < 0) {
                minTimestamp = timestamp;
            }

            if (timestamp.compareTo(maxTimestamp) > 0) {
                maxTimestamp = timestamp;
            }
        }

        linesRead++;
        linesReadInFile++;
    }

    public void printRunSummary() {
        System.out.printf("----------------------\nParsed %d log records, min timestamp: '%s', max timestamp: %s%n",
                linesRead, minTimestamp, maxTimestamp);

        System.out.printf("Query all Azure SDK logs and expand properties:\n" +
                "\ttraces | where cloud_RoleInstance  == \"%s\" and cloud_RoleName == \"%s\"%n"
                + "| where isnotnull(customDimensions[\"az.sdk.message\"])%n"
                + "| evaluate bag_unpack(customDimensions)\n"
                + "| sort by tolong(\"line\"), tostring(\"connectionId\") asc\n", uniqueId, runName);

    }
}

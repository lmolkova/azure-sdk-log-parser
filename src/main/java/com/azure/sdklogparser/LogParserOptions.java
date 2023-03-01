package com.azure.sdklogparser;

import com.beust.jcommander.Parameter;

public class LogParserOptions {
    public static final String APPLICATION_INSIGHTS_CONNECTION_STRING_NAME = "APPLICATIONINSIGHTS_CONNECTION_STRING";

    /**
     * TODO: we can support archived tar.gz, zip. we can support other sources: blob
     */
    @Parameter(names = {"-f", "--file"}, description = "Path to log file, log directory, or zip archive to parse.",
            order = 0, required = true)
    private String fileOrDirectory;

    @Parameter(names = {"-d", "--dry-run"}, description = "dry run, when set, does not send data to Log Analytics "
            + " and parses first 2 lines of each file")
    private boolean isDryRun = false;

    @Parameter(names = {"-r", "--run-id"}, description = "Name to identify this run, by default fileName is used. "
            + "Parser will add unique id.")
    private String runId;

    @Parameter(names = {"-c", "--connection-string"}, description = "Application Insights connection string (or pass it in "
            + APPLICATION_INSIGHTS_CONNECTION_STRING_NAME + " env var). If not set, it will be a dry-run")
    private String connectionString = System.getenv(APPLICATION_INSIGHTS_CONNECTION_STRING_NAME);

    @Parameter(names = {"-z", "--unzip"}, description = "Unzip file before processing (will be done if file extension is 'zip')")
    private boolean unzipFile = false;

    @Parameter(names = {"-m", "--max-lines-per-file"}, description = "Max number of lines to process in dry run.")
    private Long maxLinesPerFile = 3L;

    @Parameter(names = {"-h", "--help"}, description = "Print help", help = true)
    private boolean printHelp;

    public String getFileOrDirectory() {
        return fileOrDirectory;
    }

    public boolean isDryRun() {
        return isDryRun;
    }

    public void setIsDryRun(boolean isDryRun) {
        this.isDryRun = isDryRun;
    }

    public String getRunId() {
        return runId;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public boolean unzipFile() {
        return unzipFile;
    }

    public Long getMaxLinesPerFile() {
        return maxLinesPerFile;
    }

    public boolean isPrintHelp() {
        return printHelp;
    }

    public String getCommandName() {
        return "";
    }

    public String getExamples() {
        return "";
    }
}

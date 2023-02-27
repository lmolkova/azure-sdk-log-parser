package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;

public class LogParserOptions {
    public static final String APPLICATION_INSIGHTS_CONNECTION_STRING_NAME = "APPLICATIONINSIGHTS_CONNECTION_STRING";
    /**
     * TODO: we can support archived tar.gz, zip. we can support other sources: blob
     */
    @Parameter(names = {"-f", "--file"}, description = "Path to log file, log directory, or zip archive to parse.",
            required = true)
    private String fileOrDirectory;

    @Parameter(names = {"-l", "--layout"}, description = "Log layout, defaults to \"<date> <time> <level> <thread> <class> \". "
            + "Message is the last one and not specified in the layout.",
            converter = LayoutConverter.class)
    private Layout layout = Layout.DEFAULT;

    @Parameter(names = {"-d", "--dry-run"}, description = "dry run, when set, does not send data to Log Analytics "
            + " and parses first 2 lines of each file")
    private boolean isDryRun = false;

    @Parameter(names = {"-r", "--run-id"}, description = "Name to identify this run, by default fileName is used. "
            + "Parser will add unique id.")
    private String runId;

    @Parameter(names = {"-c", "--connection-string"}, description = "Azure Monitor connection string (or pass it in "
            + APPLICATION_INSIGHTS_CONNECTION_STRING_NAME + " env var). If not set, it will be a dry-run")
    private String connectionString = System.getenv(APPLICATION_INSIGHTS_CONNECTION_STRING_NAME);

    @Parameter(names = {"-z", "--unzip"}, description = "Unzip file before processing (will be done if file extension is 'zip')")
    private boolean unzipFile = false;

    @Parameter(names = {"-m", "--max-lines-per-file"}, description = "Max lines to print, none by default")
    private Long maxLinesPerFile = 3L;

    @Parameter(names = {"-h", "--help"}, description = "Print help", help = true)
    private boolean printHelp;

    public String getFileOrDirectory() {
        return fileOrDirectory;
    }

    public Layout getLayout() {
        return layout;
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

    public static String getExamples() {
        return "Example 1 (custom log layout): java -jar log-parser.jar -f c:\\downloads\\logs.zip -l \"<date> <time> <level> [<thread>] <class> - \" -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "Example 2 (default layout, print parsed logs): java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar  -f c:\\downloads\\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "when specifying layout, add separator at the end. <date>, <time> and <level> are magic words, other fields are arbitrary.\n"
                + "Example 3 (dry run, will print all parsed logs): java -jar log-parser.jar -f c:\\downloads\\logs -d\n\n";
    }

    private static final class LayoutConverter implements IStringConverter<Layout> {
        @Override
        public Layout convert(String s) {
            return Layout.fromString(s);
        }
    }
}

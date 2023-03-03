package com.azure.sdklogparser;

import com.azure.sdklogparser.util.TokenType;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * When the log is a json object.
 */
@Parameters(commandDescription = "Parse a log where each line is a JSON object.")
public class JsonLogParserOptions extends LogParserOptions {
    public static final String COMMAND_NAME = "json";

    @Parameter(names = {"-m", "--message"},
            description = "Key name in JSON object containing the actual log message.",
            order = 1)
    private String messageKey = TokenType.MESSAGE.getValue();

    @Parameter(names = {"-t", "--timestamp"},
            description = "Key name in JSON object containing the timestamp.",
            order = 1)
    private String timestamp = TokenType.TIMESTAMP.getValue();

    @Parameter(names = {"-l", "--logger"},
            description = "Key name in JSON object containing the logger's name.",
            order = 1)
    private String logger = TokenType.LOGGER.getValue();

    @Parameter(names = {"-ll", "--logLevel"},
            description = "Key name in JSON object containing the log level.",
            order = 1)
    private String logLevel = TokenType.LOG_LEVEL.getValue();

    @Parameter(names = {"-th", "--thread"},
            description = "Key name in JSON object containing the thread.",
            order = 1)
    private String thread = TokenType.THREAD.getValue();

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    @Override
    public String getExamples() {
        return "java -jar log-parser json -k message -f \"C:\\my-json.log\""
                + "\n\tParses a log where the SDK message is stored in the \"message\" key of each JSON log object.";
    }
}

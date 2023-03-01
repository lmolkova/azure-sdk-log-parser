package com.azure.sdklogparser;

import com.azure.sdklogparser.util.TokenType;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * When the log is a json object.
 */
@Parameters(commandDescription = "Parsing a log that is a JSON object with each log line as an object.")
public class JsonLogParserOptions extends LogParserOptions {
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

    public String getTimestamp() {
        return timestamp;
    }

    public String getLogger() {
        return logger;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getThread() {
        return thread;
    }

    @Override
    public String getCommandName() {
        return "json";
    }

    @Override
    public String getExamples() {
        return "java -jar log-parser json -k message -f \"C:\\my-json.log\""
                + "\n\tParses a log where the SDK message is stored in the \"message\" key of each JSON log object.";
    }
}

package com.azure.sdklogparser;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * When the log is a json object.
 */
@Parameters(commandDescription = "Parsing a log that is a JSON object with each log line as an object.")
public class JsonLogParserOptions extends LogParserOptions {
    @Parameter(names = {"-k", "--sdkMessageKey"},
            description = "Key name in JSON object containing the SDK's log message.",
            required = true, order = 1)
    private String sdkMessageKey;

    public String getSdkMessageKey() {
        return sdkMessageKey;
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

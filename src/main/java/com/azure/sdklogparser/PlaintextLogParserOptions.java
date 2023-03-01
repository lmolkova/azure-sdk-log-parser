package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * When the log is a series of plaintext lines.
 */
@Parameters(commandDescription = "Parsing a log that is plaintext.")
public class PlaintextLogParserOptions extends LogParserOptions {
    @Parameter(names = {"-l", "--layout"}, description = "Log layout, defaults to \"<date> <time> <level> <thread> <class> \". "
            + "Message is the last one and not specified in the layout.",
            order = 1, converter = LayoutConverter.class)
    private Layout layout = Layout.DEFAULT;

    private static final class LayoutConverter implements IStringConverter<Layout> {
        @Override
        public Layout convert(String s) {
            return Layout.fromString(s);
        }
    }

    public Layout getLayout() {
        return layout;
    }

    @Override
    public String getCommandName() {
        return "plain";
    }

    @Override
    public String getExamples() {
        return "Example 1\t(custom log layout): java -jar log-parser.jar -f c:\\downloads\\logs.zip -l \"<date> <time> <level> [<thread>] <class> - \" -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "Example 2\t(default layout, print parsed logs): java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar  -f c:\\downloads\\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "when specifying layout, add separator at the end. <date>, <time> and <level> are magic words, other fields are arbitrary.\n"
                + "Example 3\t(dry run, will print all parsed logs): java -jar log-parser.jar -f c:\\downloads\\logs -d\n\n";
    }
}

package com.azure.sdklogparser;

import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.TokenType;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * When the log is a series of plaintext lines.
 */
@Parameters(commandDescription = "Parse a log that is plaintext.")
public class PlaintextLogParserOptions extends LogParserOptions {
    public static final String COMMAND_NAME = "plain";

    @Parameter(names = {"-l", "--layout"}, description = "Layout of each log line. Each parameter name is enclosed "
            + "with < >. Parameters supported are in the \"KNOWN PARAMETERS\" section. "
            + "If parameter <message> is not specified, it is assumed to be at the end of the log line. See EXAMPLES.",
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
    public String getExamples() {
        return "Example 1\t(custom log layout): java -jar log-parser.jar -f c:\\downloads\\logs.zip -l \"<date> <time> <level> [<thread>] <class> - \" -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "Example 2\t(default layout, print parsed logs): java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar  -f c:\\downloads\\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n"
                + "when specifying layout, add separator at the end. <date>, <time> and <level> are magic words, other fields are arbitrary.\n"
                + "Example 3\t(dry run, will print all parsed logs): java -jar log-parser.jar -f c:\\downloads\\logs -d\n\n";
    }

    public static String getSupportedParameters() {
        final StringBuilder builder = new StringBuilder("--------- KNOWN PARAMETERS ---------\n\n");
        final String tokenTypes = Arrays.stream(TokenType.values())
                .map(value -> {
                    final String cmd = "<" + value.getValue() + ">";
                    return String.format("%-12s  %s", cmd, value.getDescription());
                })
                .collect(Collectors.joining("\n"));

        builder.append(tokenTypes).append("\n");
        return builder.toString();
    }
}

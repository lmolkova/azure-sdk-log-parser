package com.azure.sdklogparser;

import com.azure.sdklogparser.util.ArchiveHelper;
import com.azure.sdklogparser.util.Layout;
import com.azure.sdklogparser.util.RunInfo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;

public class LogParserApp {

    public static void main(String[] args) throws IOException, ParseException {

        var helpFormatter = new HelpFormatter();
        helpFormatter.setOptionComparator(null);
        helpFormatter.setWidth(256);

        String example = "Example 1 (custom log layout): java -jar log-parser.jar -f c:\\downloads\\logs.zip -l \"<date> <time> <level> [<thread>] <class> - \" -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n";
        example += "Example 2 (default layout, print parsed logs): java -jar -Dorg.slf4j.simpleLogger.defaultLogLevel=debug log-parser.jar  -f c:\\downloads\\logs.log -c InstrumentationKey=secret;IngestionEndpoint=https://westus2-2.in.applicationinsights.azure.com/\n";
        example += "    when specifying layout, add separator at the end. <date>, <time> and <level> are magic words, other fields are arbitrary.\n";
        example += "Example 3 (dry run, will print all parsed logs): java -jar log-parser.jar -f c:\\downloads\\logs -d\n\n";

        Options options = new Options();

        // we can support archived tar.gz, zip
        // we can support other sources: blob
        options.addRequiredOption("f", "file", true, "REQUIRED. Log file (directory or zip archive) name");
        options.addOption("l",  "layout", true, "log layout, defaults to \"<date> <time> <level> <thread> <class> \". Message is the last one and not specified in the layout.");
        options.addOption("d", "dry-run", false, "dry run, when set, does nto send data to Log Analytics and parses first 2 lines of each file");
        options.addOption("r", "run-id", true, "Name to identify this run, by default fileName is used. Parser will add unique id.");
        options.addOption("c", "connection-string", true, "Azure Monitor connection string (or pass it in APPLICATIONINSIGHTS_CONNECTION_STRING env var). If not set, it will be a dry-run");
        options.addOption("z", "unzip", false, "Unzip file before processing (will be done if file extension is 'zip')");
        options.addOption("m", "max-lines-per-file", true, "Max lines to print, none by default");
        options.addOption("h", "help", false, "Print help");


        // -l "<date> <time> <level> [<thread>] <class> - "

        var parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            helpFormatter.printHelp("log-parser", example, options, null, true);
            throw ex;
        }

        if (cmd.hasOption("h")) {
            helpFormatter.printHelp("log-parser", example, options, null, true);
            return;
        }

        if (!cmd.hasOption("f")) {
            helpFormatter.printHelp("log-parser", example, options, null, true);
            return;
        }


        var fileName = cmd.getOptionValue("f");
        var connectionString = cmd.hasOption("c") ?  cmd.getOptionValue("c") :  System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
        var runIdPrefix = cmd.hasOption("r") ? cmd.getOptionValue("r") : Paths.get(fileName).getFileName().toString();
        var dryRun = cmd.hasOption("d");
        if (connectionString == null && !dryRun) {
            System.out.println("Connection string is missing, making it a dry-run");
            dryRun = true;
        }

        String maxLinesPerFileStr = cmd.getOptionValue("m");
        long maxLinesPerFile = dryRun ? 3 : Long.MAX_VALUE;
        if (maxLinesPerFileStr != null) {
            maxLinesPerFile = Long.parseLong(maxLinesPerFileStr);
        }

        var layout = cmd.hasOption("l") ? Layout.fromString(cmd.getOptionValue("l")) : Layout.DEFAULT;

        Path pathToFile = getPathAndUnzipIfNeeded(fileName, cmd.hasOption("z"));

        RunInfo run = new RunInfo(runIdPrefix, dryRun, maxLinesPerFile);
        var logParser = new LogParser(connectionString, run);

        for (var file : listFiles(pathToFile)) {
            run.nextFile(file.getAbsolutePath());
            try (var fileReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                logParser.parse(fileReader, layout);
            }
        }

        run.printRunSummary();
    }

    private static Path getPathAndUnzipIfNeeded(String fileName, boolean unzip) throws IOException {
        Path pathToFile = Paths.get(fileName);
        if (unzip || FilenameUtils.getExtension(fileName).equals("zip")) {
            pathToFile = Files.createTempDirectory(null);
            if (!ArchiveHelper.unzip(fileName, pathToFile.toString())){
                System.out.println("Error: can't unzip " + fileName);
                System.exit(1);
            }
        }

        return pathToFile;
    }

    private static Collection<File> listFiles(Path pathToFile) {
        if (Files.isDirectory(pathToFile)) {
            return FileUtils.listFiles(
                    pathToFile.toFile(),
                    new String[] {"log"},
                    true);
        }

        return Collections.singletonList(pathToFile.toFile());
    }
}

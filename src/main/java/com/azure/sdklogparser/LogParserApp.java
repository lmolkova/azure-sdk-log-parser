package com.azure.sdklogparser;

import com.azure.sdklogparser.util.ArchiveHelper;
import com.azure.sdklogparser.util.PrintTelemetryClient;
import com.azure.sdklogparser.util.RunInfo;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class LogParserApp {

    public static void main(String[] args) {
        // -l "<date> <time> <level> [<thread>] <class> - "
        final PlaintextLogParserOptions plainTextCommand = new PlaintextLogParserOptions();
        final JsonLogParserOptions jsonCommand = new JsonLogParserOptions();

        final JCommander jCommander = JCommander.newBuilder()
                .addCommand(plainTextCommand.getCommandName(), plainTextCommand)
                .addCommand(jsonCommand.getCommandName(), jsonCommand)
                .build();
        jCommander.setProgramName("log-parser");

        try {
            jCommander.parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getLocalizedMessage());
            System.err.println();

            printHelp(jCommander, plainTextCommand, jsonCommand);
            return;
        }

        final String command = jCommander.getParsedCommand();

        final LogParserOptions optionsToUse;
        if (plainTextCommand.getCommandName().equalsIgnoreCase(command)) {
            if (plainTextCommand.isPrintHelp()) {
                printHelp(jCommander, plainTextCommand, jsonCommand);
                return;
            }

            optionsToUse = plainTextCommand;
        } else if (jsonCommand.getCommandName().equalsIgnoreCase(command)) {
            if (jsonCommand.isPrintHelp()) {
                printHelp(jCommander, plainTextCommand, jsonCommand);
                return;
            }

            optionsToUse = jsonCommand;
        } else {
            System.out.println("Arguments did not match any command sets.");
            printHelp(jCommander, plainTextCommand, jsonCommand);
            return;
        }

        final RunInfo runInformation = getRunInformation(optionsToUse);
        final TelemetryClient telemetryClient = getTelemetryClient(optionsToUse, runInformation);
        final Path pathToFile = getPathAndUnzipIfNeeded(optionsToUse.getFileOrDirectory(), optionsToUse.unzipFile());

        final LogParser logParser = new LogParser(telemetryClient, runInformation);

        for (var file : listFiles(pathToFile)) {
            runInformation.nextFile(file.getAbsolutePath());
            try (var fileReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                logParser.parse(fileReader, plainTextCommand.getLayout());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read file: " + file, e);
            }
        }

        runInformation.printRunSummary();
    }

    private static TelemetryClient getTelemetryClient(LogParserOptions options, RunInfo runInfo) {
        final String connectionString;
        if (options.getConnectionString() != null) {
            connectionString = options.getConnectionString();
        } else {
            System.out.println("Connection string is missing, making it a dry-run");
            options.setIsDryRun(true);
            connectionString = null;
        }

        final TelemetryClient telemetryClient;
        if (options.isDryRun() && connectionString != null) {
            telemetryClient = new PrintTelemetryClient();
        } else {
            TelemetryConfiguration config = new TelemetryConfiguration();
            TelemetryInitializer initializer = telemetry -> {
                telemetry.getContext().getCloud().setRole(runInfo.getRunName());
                telemetry.getContext().getCloud().setRoleInstance(runInfo.getUniqueId());
            };

            config.getTelemetryInitializers().add(initializer);
            config.setConnectionString(Objects.requireNonNull(connectionString));
            config.setChannel(new InProcessTelemetryChannel(config));

            telemetryClient = new TelemetryClient(config);
        }

        return telemetryClient;
    }

    private static RunInfo getRunInformation(LogParserOptions options) {
        final String fileName = options.getFileOrDirectory();
        final String runIdPrefix = options.getRunId() != null
                ? options.getRunId()
                : Paths.get(fileName).getFileName().toString();
        final long numberOfLinesToProcess = options.isDryRun() ? options.getMaxLinesPerFile() : Long.MAX_VALUE;

        return new RunInfo(runIdPrefix, options.isDryRun(), numberOfLinesToProcess);
    }

    private static void process(RunInfo runInfo, JsonLogParserOptions options) {

    }

    private static void process(RunInfo runInfo, PlaintextLogParserOptions options) {

    }

    private static void printHelp(JCommander jCommander, LogParserOptions... command) {
        jCommander.usage();
        System.out.println("Examples\n\n");

        Arrays.stream(command).forEach(option -> {
            System.out.println(option.getExamples());
            System.out.println();
        });
    }

    private static Path getPathAndUnzipIfNeeded(String fileName, boolean unzip) {
        Path pathToFile = Paths.get(fileName);
        if (unzip || FilenameUtils.getExtension(fileName).equals("zip")) {
            try {
                pathToFile = Files.createTempDirectory(null);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create temporary file directory.", e);
            }

            if (!ArchiveHelper.unzip(fileName, pathToFile.toString())) {
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
                    new String[]{"log"},
                    true);
        }

        return Collections.singletonList(pathToFile.toFile());
    }
}

package com.azure.sdklogparser;

import com.azure.sdklogparser.util.ArchiveHelper;
import com.azure.sdklogparser.util.RunInfo;
import com.beust.jcommander.JCommander;
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
import java.util.Collection;
import java.util.Collections;

public class LogParserApp {

    public static void main(String[] args) {
        // -l "<date> <time> <level> [<thread>] <class> - "
        final LogParserOptions options = new LogParserOptions();

        final JCommander jCommander = JCommander.newBuilder()
                .addObject(options)
                .build();

        jCommander.parse(args);

        if (options.isPrintHelp()) {
            jCommander.usage();
            System.out.println(LogParserOptions.getExamples());
            return;
        }

        final String fileName = options.getFileOrDirectory();
        final String connectionString;
        if (options.getConnectionString() != null) {
            connectionString = options.getConnectionString();
        } else {
            System.out.println("Connection string is missing, making it a dry-run");
            options.setIsDryRun(true);
            connectionString = null;
        }

        final String runIdPrefix = options.getRunId() != null
                ? options.getRunId()
                : Paths.get(fileName).getFileName().toString();

        final Path pathToFile = getPathAndUnzipIfNeeded(fileName, options.unzipFile());

        RunInfo run = new RunInfo(runIdPrefix, options.isDryRun(), options.getMaxLinesPerFile());
        var logParser = new LogParser(connectionString, run);

        for (var file : listFiles(pathToFile)) {
            run.nextFile(file.getAbsolutePath());
            try (var fileReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                logParser.parse(fileReader, options.getLayout());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read file: " + file, e);
            }
        }

        run.printRunSummary();
    }

    private static Path getPathAndUnzipIfNeeded(String fileName, boolean unzip) {
        Path pathToFile = Paths.get(fileName);
        if (unzip || FilenameUtils.getExtension(fileName).equals("zip")) {
            try {
                pathToFile = Files.createTempDirectory(null);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create temporary file directory.", e);
            }

            try {
                if (!ArchiveHelper.unzip(fileName, pathToFile.toString())) {
                    System.out.println("Error: can't unzip " + fileName);
                    System.exit(1);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to unzip file: " + fileName, e);
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

package com.vackosar.gitflowincrementalbuild;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessUtils.class);

    private static final String LINE_PREFIX = "> ";

    public static String startAndWaitForProcess(String... args) throws InterruptedException, IOException {
        return startAndWaitForProcess(Arrays.asList(args), Paths.get("."), line -> true);
    }

    public static String startAndWaitForProcess(List<String> args, Path dir, Predicate<String> lineFilterPredicate) throws InterruptedException, IOException {
        final Process process = new ProcessBuilder(cmdArgs(args))
                .redirectErrorStream(true)
                .directory(dir.toFile())
                .start();
        final List<String> lines = captureOutput(process.getInputStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            LOGGER.error("stdOut/stdErr:\n{}", linesToString(lines, line -> true));
            Assertions.fail("Process failed with return code " + returnCode);
        }
        return linesToString(lines, lineFilterPredicate);
    }
    private static List<String> captureOutput(final InputStream inStream) {
        final List<String> capturedLines = new ArrayList<>(200);
        // https://www.baeldung.com/run-shell-command-in-java#Output
        new Thread(() -> {
            new BufferedReader(new InputStreamReader(inStream, Charset.defaultCharset())).lines().forEach(capturedLines::add);
        }).start();
        return capturedLines;
    }

    private static List<String> cmdArgs(List<String> args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), args.stream()).collect(Collectors.toList())
            : args;
    }

    private static String linesToString(List<String> lines, Predicate<String> lineFilterPredicate) {
        final StringBuilder builder = new StringBuilder(lines.size() * 120);
        lines.stream()
                .filter(lineFilterPredicate)
                .forEach(l -> builder.append(LINE_PREFIX).append(l).append("\n"));
        return builder.toString();
    }
}

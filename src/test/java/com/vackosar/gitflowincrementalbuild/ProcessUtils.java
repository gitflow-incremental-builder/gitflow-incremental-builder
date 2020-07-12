package com.vackosar.gitflowincrementalbuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessUtils.class);

    public static String startAndWaitForProcess(String... args) throws InterruptedException, IOException {
        return startAndWaitForProcess(Arrays.asList(args), new File("."));
    }

    public static String startAndWaitForProcess(List<String> args, File dir) throws InterruptedException, IOException {
        final Process process = new ProcessBuilder(cmdArgs(args))
                .redirectErrorStream(true)
                .directory(dir)
                .start();
        final StringBuilder outBuilder = captureOutput(process.getInputStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            LOGGER.error("stdOut/stdErr:\n{}", outBuilder.toString());
            Assertions.fail("Process failed with return code " + returnCode);
        }
        return outBuilder.toString();
    }

    private static StringBuilder captureOutput(final InputStream inStream) {
        final StringBuilder outBuilder = new StringBuilder(10240);
        // https://www.baeldung.com/run-shell-command-in-java#Output
        new Thread(() -> {
            new BufferedReader(new InputStreamReader(inStream, Charset.defaultCharset())).lines()
                    .forEach(l -> outBuilder.append("> ").append(l).append("\n"));
        }).start();
        return outBuilder;
    }

    private static List<String> cmdArgs(List<String> args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), args.stream()).collect(Collectors.toList())
            : args;
    }
}

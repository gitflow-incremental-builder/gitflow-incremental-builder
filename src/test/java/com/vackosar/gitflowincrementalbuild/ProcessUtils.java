package com.vackosar.gitflowincrementalbuild;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
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
        final AtomicReference<String> outHolder = captureOutput(process.getInputStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            LOGGER.error("stdOut/stdErr:\n{}", outHolder.get());
            Assert.fail("Process failed with return code " + returnCode);
        }
        return outHolder.get();
    }

    private static AtomicReference<String> captureOutput(final InputStream inStream) {
        final AtomicReference<String> outHolder = new AtomicReference<>();
        new Thread(() -> {
            try (Scanner scanner = new Scanner(inStream)) {
                outHolder.set(scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "");
            }
        }).start();
        return outHolder;
    }

    private static List<String> cmdArgs(List<String> args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), args.stream()).collect(Collectors.toList())
            : args;
    }
}

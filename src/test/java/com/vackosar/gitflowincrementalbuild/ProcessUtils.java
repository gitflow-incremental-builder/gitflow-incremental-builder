package com.vackosar.gitflowincrementalbuild;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessUtils.class);

    public static String awaitProcess(Process process) throws InterruptedException {
        final String stdOut = convertStreamToString(process.getInputStream());
        final String stdErr = convertStreamToString(process.getErrorStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            LOGGER.info("stdOut:\n{}", stdOut);
            LOGGER.error("stdErr:\n{}", stdErr);
            Assert.fail("Process failed with return code " + returnCode);
        }
        return stdOut;
    }

    public static String convertStreamToString(java.io.InputStream is) {
        try (java.util.Scanner s = new java.util.Scanner(is)) {
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        }
    }

    public static List<String> cmdArgs(String... args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), Arrays.stream(args)).collect(Collectors.toList())
            : Arrays.asList(args);
    }
}

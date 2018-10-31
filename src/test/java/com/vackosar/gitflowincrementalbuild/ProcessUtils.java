package com.vackosar.gitflowincrementalbuild;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;

public class ProcessUtils {

    public static String awaitProcess(Process process) throws InterruptedException {
        final String stdOut = convertStreamToString(process.getInputStream());
        final String stdErr = convertStreamToString(process.getErrorStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            System.err.println(stdOut);
            System.err.println(stdErr);
            Assert.fail("Process failed with return code " + returnCode);
        }
        return stdOut;
    }

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static List<String> cmdArgs(String... args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), Arrays.stream(args)).collect(Collectors.toList())
            : Arrays.asList(args);
    }
}

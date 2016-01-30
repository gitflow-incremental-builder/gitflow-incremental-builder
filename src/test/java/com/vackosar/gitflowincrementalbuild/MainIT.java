package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class MainIT {

    @Test
    public void list() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Process process = execute();
        Assert.assertEquals("child1" + System.lineSeparator(), convertStreamToString(process.getInputStream()));
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        repoMock.close();
    }

    @Test
    public void build() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Process process = execute();
        final String modules = convertStreamToString(process.getInputStream()).replaceAll(System.lineSeparator(), "");
//        Process build = null;
//        try {
//            build = executeBuild(modules);
//        } catch (Exception e) {
//            System.out.println(e);
//        }
//        System.out.println(convertStreamToString(build.getInputStream()));
//        System.out.println(convertStreamToString(build.getErrorStream()));
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        repoMock.close();
    }

    private Process execute() throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder()
                        .command("java", "-cp", "../../target/*", "com.vackosar.gitflowincrementalbuild.Main", "parent/pom.xml")
                        .directory(new File("tmp/repo"))
                        .start();
        process.waitFor();
        return process;
    }

    private Process executeBuild(String modules) throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder("mvn", "-pl", modules, "parent/pom.xml")
                        .directory(new File("tmp/repo"))
                        .start();
        process.waitFor();
        return process;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

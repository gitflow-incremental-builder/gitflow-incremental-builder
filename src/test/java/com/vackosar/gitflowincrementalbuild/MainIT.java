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
        Assert.assertEquals("child2\\subchild2,child3" + System.lineSeparator(), convertStreamToString(process.getInputStream()));
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        repoMock.close();
    }

    @Test
    public void build() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Process process = execute();
        final String modules = convertStreamToString(process.getInputStream()).replaceAll(System.lineSeparator(), "");
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        Process build = executeBuild(modules);
        final String output = convertStreamToString(build.getInputStream());
        System.out.println(output);
        System.out.println(convertStreamToString(build.getErrorStream()));

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
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
                new ProcessBuilder("cmd", "/c", "mvn", "compile", "-pl", modules)
                        .directory(new File("tmp/repo/parent"))
                        .start();
        process.waitFor();
        return process;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

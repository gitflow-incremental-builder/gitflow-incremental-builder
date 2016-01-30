package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class MainIT extends RepoTest {

    @Test
    public void list() throws Exception {
        final GIBMock gibMock = new GIBMock();
        final Process process = gibMock.execute(Paths.get("parent/pom.xml"));
        Assert.assertEquals("child2\\subchild2,child3" + System.lineSeparator(), convertStreamToString(process.getInputStream()));
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        gibMock.close();
    }

    @Test
    public void build() throws Exception {
        final GIBMock gibMock = new GIBMock();
        final Process process = gibMock.execute(Paths.get("parent/pom.xml"));
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

        gibMock.close();
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

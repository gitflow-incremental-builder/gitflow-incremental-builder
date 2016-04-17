package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.mocks.GIBMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
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
        Assert.assertEquals("child2\\subchild2,child3,child4" + System.lineSeparator(), convertStreamToString(process.getInputStream()));
        gibMock.close();
    }

    @Test
    public void buildWithExtension() throws Exception {
        final String output = executeSimplyBuild();
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
    }

    @Test
    public void build() throws Exception {
        final GIBMock gibMock = new GIBMock();
        final Process process = gibMock.execute(Paths.get("parent/pom.xml"));
        final String modules = convertStreamToString(process.getInputStream()).replaceAll(System.lineSeparator(), "");
        Process build = executeBuild(modules);
        final String output = convertStreamToString(build.getInputStream());
        System.out.println(output);
        System.out.println(convertStreamToString(build.getErrorStream()));

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));

        gibMock.close();
    }

    @Test
    public void mavenTest() throws IOException, InterruptedException {
        final Process build = executeBuild("child2/subchild2");
        final String output = convertStreamToString(build.getInputStream());
        System.out.println(output);
        Assert.assertEquals(0, build.waitFor());
    }

    private String executeSimplyBuild() throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder("cmd", "/c", "mvn", "compile", "--file", "parent\\pom.xml")
                        .directory(new File("tmp/repo"))
                        .start();
        String output = convertStreamToString(process.getInputStream());
        System.out.println(convertStreamToString(process.getErrorStream()));
        process.waitFor();
        return output;
    }

    private Process executeBuild(String modules) throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder("cmd", "/c", "mvn", "compile", "-amd", "-pl", modules, "--file", "parent\\pom.xml")
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

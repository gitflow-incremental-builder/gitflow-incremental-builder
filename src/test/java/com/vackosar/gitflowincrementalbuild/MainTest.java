package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class MainTest {

    @Test
    public void list() throws Exception {
        RepoMock repoMock = new RepoMock();
        String workDir = DiffListerTest.TEST_WORK_DIR + "tmp/repo/";
        System.setProperty("user.dir", workDir);
        final Process process =
                new ProcessBuilder("java", "-cp", "../../target/*", "com.vackosar.gitflowincrementalbuild.Main", "parent/pom.xml")
                        .directory(new File("tmp/repo"))
                        .start();
        while (process.isAlive()) {
            Thread.sleep(20);
        }
        Assert.assertEquals("child1" + System.lineSeparator(), convertStreamToString(process.getInputStream()));
        Assert.assertEquals("", convertStreamToString(process.getErrorStream()));
        repoMock.close();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

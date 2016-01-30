package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MainTest extends RepoTest {

    @Test
    public void list() throws Exception {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream errout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        System.setErr(new PrintStream(errout));
        Main.main(new String[]{RepoMock.WORK_DIR + "/parent/pom.xml"});
        Assert.assertEquals("child2\\subchild2,child3" + System.lineSeparator(), stdout.toString());
        Assert.assertEquals("", errout.toString());
    }
}

package com.vackosar.gitflowincrementalbuild;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintStream;

public class MainTest {

    @Test
    public void list() throws Exception {
        RepoMock repoMock = new RepoMock();
        final ByteOutputStream stdout = new ByteOutputStream();
        final ByteOutputStream errout = new ByteOutputStream();
        System.setOut(new PrintStream(stdout));
        System.setErr(new PrintStream(errout));
        Main.main(new String[]{RepoMock.WORK_DIR + "/parent/pom.xml"});
        Assert.assertEquals("child1" + System.lineSeparator(), stdout.toString());
        Assert.assertEquals("", errout.toString());
        repoMock.close();
    }
}

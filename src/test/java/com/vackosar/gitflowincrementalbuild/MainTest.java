package com.vackosar.gitflowincrementalbuild;

import com.vackosar.gitflowincrementalbuild.boundary.Main;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class MainTest extends RepoTest {

    @Test
    public void list() throws Exception {
        execute(RepoMock.WORK_DIR + "/parent/pom.xml");
    }

    @Test
    public void listRelatively() throws Exception {
        execute("parent/pom.xml");
    }

    private String execute(String pom) throws GitAPIException, IOException {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream errout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        System.setErr(new PrintStream(errout));
        Main.main(new String[]{pom});
        Assert.assertEquals("", errout.toString());
        Assert.assertEquals("child2\\subchild2,child3,child4" + System.lineSeparator(), stdout.toString());
        return stdout.toString();
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;

public class ArgumentsTest {

    public static final Path WORK_DIR = Paths.get(".");
    public static final String POM_XML = "pom.xml";
    public static final Path POM = Paths.get("pom.xml");

    @Test(expected = ExitException.class) public void stopAndPrintHelp() throws IOException {
        new Arguments(new String[]{POM_XML, "-nonsense"}, WORK_DIR);
    }

    @Test public void parsePomOnly() throws IOException {
        Arguments arguments = new Arguments(new String[]{POM_XML}, WORK_DIR);
        Assert.assertEquals(POM, arguments.pom.getFileName());
        Assert.assertFalse(arguments.key.isPresent());
    }

    @Test public void parsePomAndKey() throws IOException {
        Arguments arguments = new Arguments(new String[]{POM_XML, "-k", POM_XML}, WORK_DIR);
        Assert.assertEquals(POM, arguments.pom.getFileName());
        Assert.assertEquals(POM, arguments.key.get().getFileName());
    }

    @Test public void parseKeyAndPom() throws IOException {
        Arguments arguments = new Arguments(new String[]{"-k", POM_XML, POM_XML}, WORK_DIR);
        Assert.assertEquals(POM, arguments.pom.getFileName());
        Assert.assertEquals(POM, arguments.key.get().getFileName());
    }

    protected static class ExitException extends SecurityException {}

    private static class NoExitSecurityManager extends SecurityManager {
        @Override public void checkPermission(Permission perm) {}
        @Override public void checkPermission(Permission perm, Object context) {}
        @Override public void checkExit(int status) {throw new ExitException();}
    }

    @BeforeClass public static void setUp() throws Exception {
        System.setSecurityManager(new NoExitSecurityManager());
    }

    @AfterClass public static void tearDown() throws Exception {
        System.setSecurityManager(null);
    }
}

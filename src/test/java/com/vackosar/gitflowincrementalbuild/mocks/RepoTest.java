package com.vackosar.gitflowincrementalbuild.mocks;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

public abstract class RepoTest {

    protected LocalRepoMock localRepoMock;
    public StaticLoggerBinder staticLoggerBinder;
    protected ByteArrayOutputStream consoleOut;
    private final PrintStream normalOut;

    public RepoTest() {
        this.normalOut = System.out;
    }

    @Before
    public void before() throws IOException, URISyntaxException, GitAPIException {
        staticLoggerBinder = new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test"));
        localRepoMock = new LocalRepoMock(false);
        resetConsoleOut();
        resetProperties();
    }

    protected LocalRepoMock getLocalRepoMock() {
        return localRepoMock;
    }

    private void resetConsoleOut() {
        consoleOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consoleOut));
    }

    private void resetProperties() {
        for (Property property: Property.values()) {
            property.setValue(property.defaultValue);
        }
        Property.uncommited.setValue("false");
        Property.referenceBranch.setValue("refs/heads/develop");
        Property.compareToMergeBase.setValue("false");
    }

    @After
    public void after() throws Exception {
        localRepoMock.close();
        normalOut.print(consoleOut.toString());
    }
}

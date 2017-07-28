package com.vackosar.gitflowincrementalbuild;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public abstract class BaseRepoTest {

    protected LocalRepoMock localRepoMock;
    private StaticLoggerBinder staticLoggerBinder;
    protected ByteArrayOutputStream consoleOut;
    private final PrintStream normalOut;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public BaseRepoTest() {
        this.normalOut = System.out;
    }

    @Before
    public void before() throws Exception {
        init();
        localRepoMock = new LocalRepoMock(temporaryFolder.getRoot(), false);
    }

    protected void init() {
        staticLoggerBinder = new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test"));
        resetConsoleOut();
        resetProperties();
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
        System.setOut(normalOut);
        normalOut.print(consoleOut.toString());
    }

    protected LocalRepoMock getLocalRepoMock() {
        return localRepoMock;
    }

    protected MavenSession getMavenSessionMock() throws Exception {
        return MavenSessionMock.get(localRepoMock.getBaseCanonicalBaseFolder().toPath());
    }

}

package com.vackosar.gitflowincrementalbuild;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class BaseRepoTest {

    private final boolean useSymLinkedFolder;

    protected LocalRepoMock localRepoMock;
    private StaticLoggerBinder staticLoggerBinder;
    protected ByteArrayOutputStream consoleOut;
    private final PrintStream normalOut;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public BaseRepoTest() {
        this(false);
    }

    public BaseRepoTest(boolean useSymLinkedFolder) {
        this.normalOut = System.out;
        this.useSymLinkedFolder = useSymLinkedFolder;
    }

    @Before
    public void before() throws Exception {
        init();
        File repoBaseFolder = temporaryFolder.getRoot();

        // place repo in a sym-linked folder if requested by the concrete test class
        if (useSymLinkedFolder) {
            Path linkTarget = repoBaseFolder.toPath().resolve("link-target");
            Path link = repoBaseFolder.toPath().resolve("link");
            Files.createDirectory(linkTarget);

            // - creation of most links requires elevated rights on windows, including the ones
            //   created by Files.createSymbolicLink()
            // - creation of junctions should work without elevated rights but can only be created
            //   via mklink.exe /J ...
            if (SystemUtils.IS_OS_WINDOWS) {
                ProcessUtils.awaitProcess(new ProcessBuilder(ProcessUtils.cmdArgs(
                        "mklink", "/J", link.toAbsolutePath().toString(), linkTarget.toAbsolutePath().toString()))
                        .start());
            } else {
                Files.createSymbolicLink(link, linkTarget);
            }

            repoBaseFolder = link.toFile();
        }
        localRepoMock = new LocalRepoMock(repoBaseFolder, false);
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
        if (localRepoMock != null) {
            localRepoMock.close();
        }
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

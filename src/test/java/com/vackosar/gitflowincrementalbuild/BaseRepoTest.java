package com.vackosar.gitflowincrementalbuild;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public abstract class BaseRepoTest {

    /** The project properties for the top-level project of {@link #getMavenSessionMock() MavenSessionMock} for config init. */
    protected final Properties projectProperties = new Properties();
    private final boolean useSymLinkedFolder;
    private final TestServerType remoteRepoServerType;

    protected LocalRepoMock localRepoMock;
    /** {@link LocalRepoMock#getBaseCanonicalBaseFolder()} of {@link #localRepoMock}. */
    protected Path repoPath;

    @TempDir
    protected Path tempDir;

    public BaseRepoTest() {
        this(false, null);
    }

    public BaseRepoTest(boolean useSymLinkedFolder, TestServerType remoteRepoServerType) {
        this.useSymLinkedFolder = useSymLinkedFolder;
        this.remoteRepoServerType = remoteRepoServerType;
    }

    @BeforeEach
    protected void before(TestInfo testInfo) throws Exception {
        init();
        File repoBaseFolder = tempDir.toFile();

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
                ProcessUtils.startAndWaitForProcess("mklink", "/J", link.toAbsolutePath().toString(), linkTarget.toAbsolutePath().toString());
            } else {
                Files.createSymbolicLink(link, linkTarget);
            }

            repoBaseFolder = link.toFile();
        }
        localRepoMock = new LocalRepoMock(repoBaseFolder, remoteRepoServerType);
        repoPath = localRepoMock.getBaseCanonicalBaseFolder().toPath();
    }

    private void init() {
        projectProperties.setProperty(Property.uncommited.fullName(), "false");
        projectProperties.setProperty(Property.untracked.fullName(), "false");
        projectProperties.setProperty(Property.referenceBranch.fullName(), "refs/heads/develop");
        projectProperties.setProperty(Property.compareToMergeBase.fullName(), "false");
    }

    @AfterEach
    protected void after() throws Exception {
        if (localRepoMock != null) {
            localRepoMock.close();
        }
    }

    protected MavenSession getMavenSessionMock() throws Exception {
        return MavenSessionMock.get(repoPath, projectProperties);
    }
}

package io.github.gitflowincrementalbuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import io.github.gitflowincrementalbuilder.control.Property;
import io.github.gitflowincrementalbuilder.mocks.LocalRepoMock;
import io.github.gitflowincrementalbuilder.mocks.MavenSessionMock;
import io.github.gitflowincrementalbuilder.mocks.server.TestServerType;

public abstract class BaseRepoTest {

    /** The project properties for the top-level project of {@link #getMavenSessionMock() MavenSessionMock} for config init. */
    protected final Properties projectProperties = new Properties();
    private final boolean useSymLinkedFolder;
    private final String additionalRepoPathSubDir;
    private final TestServerType remoteRepoServerType;

    protected LocalRepoMock localRepoMock;
    /** {@link LocalRepoMock#getRepoDir()} of {@link #localRepoMock}. */
    protected Path repoPath;

    @TempDir
    protected Path repoBaseDir;

    public BaseRepoTest() {
        this.useSymLinkedFolder = false;
        this.additionalRepoPathSubDir = null;
        this.remoteRepoServerType = null;
    }

    public BaseRepoTest(boolean useSymLinkedFolder, String additionalRepoPathSubDir) {
        this.useSymLinkedFolder = useSymLinkedFolder;
        this.additionalRepoPathSubDir = additionalRepoPathSubDir;
        this.remoteRepoServerType = null;
    }

    public BaseRepoTest(TestServerType remoteRepoServerType) {
        this.useSymLinkedFolder = false;
        this.additionalRepoPathSubDir = null;
        this.remoteRepoServerType = remoteRepoServerType;
    }
    

    @BeforeEach
    protected void before(TestInfo testInfo) throws Exception {
        if (additionalRepoPathSubDir != null) {
            repoBaseDir = Files.createDirectory(repoBaseDir.resolve(additionalRepoPathSubDir));
        }

        init();

        // place repo in a sym-linked folder if requested by the concrete test class
        if (useSymLinkedFolder) {
            Path linkTarget = repoBaseDir.resolve("link-target");
            Path link = repoBaseDir.resolve("link");
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

            repoBaseDir = link;
        }
        localRepoMock = new LocalRepoMock(repoBaseDir, remoteRepoServerType);
        repoPath = localRepoMock.getRepoDir();
    }

    private void init() {
        projectProperties.setProperty(Property.uncommitted.prefixedName(), "false");
        projectProperties.setProperty(Property.untracked.prefixedName(), "false");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), "refs/heads/develop");
        projectProperties.setProperty(Property.compareToMergeBase.prefixedName(), "false");
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

    protected Path getRepoBaseDir() {
        return repoBaseDir;
    }
}

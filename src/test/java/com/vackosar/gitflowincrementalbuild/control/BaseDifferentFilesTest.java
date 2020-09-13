package com.vackosar.gitflowincrementalbuild.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.LoggerSpyUtil;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public abstract class BaseDifferentFilesTest extends BaseRepoTest {

    protected static final String FETCH_FILE = "fetch-file";
    protected static final String DEVELOP = "refs/heads/develop";
    protected static final String REMOTE_DEVELOP = "refs/remotes/origin/develop";

    protected final Logger loggerSpy = LoggerSpyUtil.buildSpiedLoggerFor(DifferentFiles.class);

    protected Path userHome;
    private File jGitUserHomeBackup;

    public BaseDifferentFilesTest(TestServerType remoteRepoServerType) {
        super(false, remoteRepoServerType);
    }

    @Override
    @BeforeEach
    protected void before(TestInfo testInfo) throws Exception {
        jGitUserHomeBackup = FS.DETECTED.userHome();
        super.before(testInfo);
        userHome = Files.createDirectory(repoBaseDir.resolve("userHome"));
        FS.DETECTED.setUserHome(userHome.toFile());
    }

    @Override
    @AfterEach
    protected void after() throws Exception {
        FS.DETECTED.setUserHome(jGitUserHomeBackup);
        super.after();
    }

    protected void addCommitToRemoteRepo(String newFileNameAndMessage) throws Exception {
        Git remoteGit = localRepoMock.getRemoteRepo().getGit();
        remoteGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        remoteGit.checkout().setName(DEVELOP).call();
        remoteGit.getRepository().getDirectory().toPath().resolve(newFileNameAndMessage).toFile().createNewFile();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage(newFileNameAndMessage).call();
        assertCommitExists(newFileNameAndMessage, remoteGit);
    }

    protected void assertCommitExists(String message, Git git) throws Exception {
        assertEquals(message, git.log().setMaxCount(1).call().iterator().next().getFullMessage());
    }

    protected Set<Path> invokeUnderTest() throws Exception {
        return invokeUnderTest(getMavenSessionMock());
    }

    protected Set<Path> invokeUnderTest(final MavenSession mavenSessionMock) throws Exception {
        mavenSessionMock.getCurrentProject().getProperties().putAll(projectProperties);

        DifferentFiles underTest = new DifferentFiles();
        GitProvider gitProvider = new GitProvider();
        Whitebox.setInternalState(underTest, gitProvider, loggerSpy);

        // isolate a possible native git invocation from the settings of the system the test is runing on
        underTest.putAdditionalNativeGitEnvironment("GIT_CONFIG_NOSYSTEM", "1");
        underTest.putAdditionalNativeGitEnvironment("HOME", userHome.toAbsolutePath().toString());

        Set<Path> result;
        try {
            result = underTest.get(new Configuration(mavenSessionMock));
        } finally {
            gitProvider.close();
        }

        assertNotNull(result, "Resulting set is unexpectedly null");
        return result;
    }
}

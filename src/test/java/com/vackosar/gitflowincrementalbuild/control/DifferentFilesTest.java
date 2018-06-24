package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.boundary.GuiceModule;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.impl.StaticLoggerBinder;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class DifferentFilesTest extends BaseRepoTest {

    private static final String REFS_HEADS_FEATURE_2 = "refs/heads/feature/2";
    private static final String HEAD = "HEAD";
    private static final String FETCH_FILE = "fetch-file";
    private static final String DEVELOP = "refs/heads/develop";
    private static final String REMOTE_DEVELOP = "refs/remotes/origin/develop";
    private Path workDir;
    private ModuleFacade moduleFacade;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Resets the user.dir system property that is manipulated by {@link #setWorkDir(Path)} to keep test classes isolated.
     */
    @Rule
    public TestRule userDirResettingRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            final String origUserDir = System.getProperty("user.dir");
            try {
                base.evaluate();
            } finally {
                System.setProperty("user.dir", origUserDir);
            }
        }
    };

    @Before
    public void before() throws Exception {
        super.init();
        workDir = temporaryFolder.getRoot().getCanonicalFile().toPath().resolve("tmp/repo/");
        setWorkDir(workDir);
        localRepoMock = new LocalRepoMock(temporaryFolder.getRoot(), true);

        Property.uncommited.setValue(Boolean.FALSE.toString());
        Property.untracked.setValue(Boolean.FALSE.toString());
    }

    @After
    public void after() throws Exception {
        if (moduleFacade != null) {
            moduleFacade.close();
        }
        super.after();
    }

    @Test(expected = ProvisionException.class)
    public void worktree() throws Exception {
        Path workDir = temporaryFolder.getRoot().toPath().resolve("tmp/repo/wrkf2");
        setWorkDir(workDir);
        getInstance(workDir).get();
    }

    @Test
    public void listIncludingUncommitted() throws Exception {
        Path repoPath = localRepoMock.getBaseCanonicalBaseFolder().toPath();
        Path modifiedFilePath = repoPath.resolve("parent/child1/src/resources/file1");
        Files.write(modifiedFilePath, "\nuncommitted".getBytes(), StandardOpenOption.APPEND);
        Property.uncommited.setValue(Boolean.TRUE.toString());

        Assert.assertTrue(getInstance(repoPath).get().contains(modifiedFilePath));

        Property.uncommited.setValue(Boolean.FALSE.toString());

        Assert.assertFalse(getInstance(repoPath).get().contains(modifiedFilePath));
    }

    @Test
    public void listIncludingUntracked() throws Exception {
        Path repoPath = localRepoMock.getBaseCanonicalBaseFolder().toPath();
        Path newFilePath = repoPath.resolve("parent/child1/src/resources/fileNew");
        Files.write(newFilePath, "\nuncommitted".getBytes(), StandardOpenOption.CREATE_NEW);
        Property.untracked.setValue(Boolean.TRUE.toString());

        Assert.assertTrue(getInstance(repoPath).get().contains(newFilePath));

        Property.untracked.setValue(Boolean.FALSE.toString());

        Assert.assertFalse(getInstance(repoPath).get().contains(newFilePath));
    }

    @Test
    public void listWithCheckout() throws Exception {
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        Property.baseBranch.setValue("refs/heads/feature/2");
        getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath()).get();
        Assert.assertTrue(consoleOut.toString().contains("Checking out base branch refs/heads/feature/2"));
    }

    @Test
    public void list() throws Exception {
        final DifferentFiles differentFiles = getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath());
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml"),
                Paths.get(workDir + "/parent/testJarDependent/src/resources/file5")
                ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listExcluding() throws Exception {
        Property.excludePathRegex.setValue(".*file2.*");
        final DifferentFiles differentFiles = getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath());
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml"),
                Paths.get(workDir + "/parent/testJarDependent/src/resources/file5")
        ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path workDir = localRepoMock.getBaseCanonicalBaseFolder().toPath().resolve("parent/child2");
        setWorkDir(workDir);
        final DifferentFiles differentFiles = getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath());
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                workDir.resolve("subchild2/src/resources/file2"),
                workDir.resolve("subchild2/src/resources/file22"),
                workDir.resolve("../child3/src/resources/file1").normalize(),
                workDir.resolve("../child4/pom.xml").normalize(),
                workDir.resolve("../testJarDependent/src/resources/file5").normalize()
        ));
        Assert.assertEquals(expected, differentFiles.get());
    }

    @Test
    public void listComparedToMergeBase() throws Exception {
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        getLocalRepoMock().getGit().checkout().setName(REFS_HEADS_FEATURE_2).call();
        getLocalRepoMock().getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        Property.baseBranch.setValue(REFS_HEADS_FEATURE_2);
        Property.compareToMergeBase.setValue("true");
        Assert.assertTrue(getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath()).get().stream().collect(Collectors.toSet()).contains(workDir.resolve("parent/feature2-only-file.txt")));
        Assert.assertTrue(consoleOut.toString().contains("59dc82fa887d9ca82a0d3d1790c6d767e738e71a"));
    }

    @Test
    public void fetch() throws Exception {
        Git remoteGit = localRepoMock.getRemoteRepo().getGit();
        remoteGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        remoteGit.checkout().setName(DEVELOP).call();
        remoteGit.getRepository().getDirectory().toPath().resolve(FETCH_FILE).toFile().createNewFile();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage(FETCH_FILE).call();
        Assert.assertEquals(FETCH_FILE, remoteGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
        Property.fetchReferenceBranch.setValue(Boolean.TRUE.toString());
        Property.referenceBranch.setValue(REMOTE_DEVELOP);
        getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath()).get();
        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, localGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
    }

    @Test
    public void fetchNonExistent() throws Exception {
        Git remoteGit = localRepoMock.getRemoteRepo().getGit();
        remoteGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        remoteGit.checkout().setName(DEVELOP).call();
        remoteGit.getRepository().getDirectory().toPath().resolve(FETCH_FILE).toFile().createNewFile();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage(FETCH_FILE).call();
        Git localGit = localRepoMock.getGit();
        localGit.branchDelete().setBranchNames(DEVELOP).call();
        localGit.branchDelete().setBranchNames(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, remoteGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
        Property.fetchReferenceBranch.setValue(Boolean.TRUE.toString());
        Property.referenceBranch.setValue(REMOTE_DEVELOP);
        getInstance(localRepoMock.getBaseCanonicalBaseFolder().toPath()).get();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        Assert.assertEquals(FETCH_FILE, localGit.log().setMaxCount(1).call().iterator().next().getFullMessage());
    }

    private static class ModuleFacade extends AbstractModule {
        private final GuiceModule guiceModule;
        private Git git;
        private Path workDir;

        public ModuleFacade(Path dir) throws Exception {
            this.guiceModule = new GuiceModule(new ConsoleLogger(), MavenSessionMock.get(dir));
            this.workDir = dir;
        }

        @Singleton @Provides public Logger provideLogger() {
            return new ConsoleLoggerManager().getLoggerForComponent("Test");
        }

        @Singleton @Provides public Git provideGit(Configuration configuration) throws IOException, GitAPIException {
            git = guiceModule.provideGit(new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test")), configuration);
            return git;
        }

        @Singleton @Provides public Configuration configuration() throws Exception {
            MavenSession mavenSession = MavenSessionMock.get(workDir);
            return new Configuration(mavenSession);
        }

        @Override
        protected void configure() {}

        public void close() {
            if (git != null) {
                if (git.getRepository() != null) {
                    git.getRepository().close();
                }
                git.close();
            }
        }

    }

    private DifferentFiles getInstance(Path dir) throws Exception {
        moduleFacade = new ModuleFacade(dir);
        return Guice.createInjector(moduleFacade).getInstance(DifferentFiles.class);
    }

    private void setWorkDir(final Path path) {
        System.setProperty("user.dir", path.toString());
    }
}

package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.boundary.GuiceModule;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.impl.StaticLoggerBinder;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class DifferentFilesTest extends RepoTest {

    private Path workDir;

    @Before
    public void before() throws GitAPIException, IOException, URISyntaxException {
        workDir = LocalRepoMock.TEST_WORK_DIR.resolve("tmp/repo/");
        setWorkDir(workDir);
        super.before();
    }

    @Test
    public void listIncludingUncommited() throws Exception {
        Property.uncommited.setValue(Boolean.TRUE.toString());
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml"),
                Paths.get(workDir + "/parent/child5/src/resources/file5"),
                Paths.get(workDir + "/parent/child1/pom.xml"),
                Paths.get(workDir + "/parent/pom.xml")
        ));
        Assert.assertEquals(expected, differentFiles.list());
    }

    @Test
    public void listWithCheckout() throws Exception {
        Property.baseBranch.setValue("refs/heads/feature/1");
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml")
        ));
        Assert.assertEquals(expected, differentFiles.list());
        Assert.assertTrue(consoleOut.toString().contains("Checking out base branch refs/heads/feature/1"));
    }

    @Test
    public void list() throws Exception {
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml")
                ));
        Assert.assertEquals(expected, differentFiles.list());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path workDir = LocalRepoMock.TEST_WORK_DIR.resolve("tmp/repo/parent/child2");
        setWorkDir(workDir);
        final DifferentFiles differentFiles = getInstance();
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                workDir.resolve("subchild2/src/resources/file2"),
                workDir.resolve("subchild2/src/resources/file22"),
                workDir.resolve("../child3/src/resources/file1").normalize(),
                workDir.resolve("../child4/pom.xml").normalize()
        ));
        Assert.assertEquals(expected, differentFiles.list());
    }

    private ModuleFacade module() throws Exception {
        return new ModuleFacade();
    }

    private static class ModuleFacade extends AbstractModule {
        private final GuiceModule guiceModule;

        public ModuleFacade() throws Exception {
            this.guiceModule = new GuiceModule(new ConsoleLogger(), getMavenSessionMock());
        }

        @Singleton @Provides public Logger provideLogger() {
            return new ConsoleLoggerManager().getLoggerForComponent("Test");
        }

        @Singleton @Provides public Git provideGit() throws IOException, GitAPIException {
            return guiceModule.provideGit(new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test")));
        }

        @Singleton @Provides public Configuration arguments() throws Exception {
            MavenSession mavenSession = getMavenSessionMock();
            return new Configuration(mavenSession);
        }

        private MavenSession getMavenSessionMock() throws Exception {
            return MavenSessionMock.get();
        }

        @Override
        protected void configure() {}
    }

    private DifferentFiles getInstance() throws Exception {
        return Guice.createInjector(module()).getInstance(DifferentFiles.class);
    }

    private void setWorkDir(final Path path) {
        System.setProperty("user.dir", path.toString());
    }
}

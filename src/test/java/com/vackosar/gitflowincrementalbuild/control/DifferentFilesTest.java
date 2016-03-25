package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.vackosar.gitflowincrementalbuild.boundary.Arguments;
import com.vackosar.gitflowincrementalbuild.boundary.Module;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class DifferentFilesTest extends RepoTest {

    @Test
    public void list() throws Exception {
        Path workDir = Paths.get(LocalRepoMock.TEST_WORK_DIR).resolve("tmp/repo/");
        final DifferentFiles differentFiles = Guice.createInjector(new ModuleFacade(workDir)).getInstance(DifferentFiles.class);
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
        Path workDir = Paths.get(LocalRepoMock.TEST_WORK_DIR).resolve("tmp/repo/parent/child2");
        final DifferentFiles differentFiles = Guice.createInjector(new ModuleFacade(workDir)).getInstance(DifferentFiles.class);
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                workDir.resolve("subchild2/src/resources/file2"),
                workDir.resolve("subchild2/src/resources/file22"),
                workDir.resolve("../child3/src/resources/file1").normalize(),
                workDir.resolve("../child4/pom.xml").normalize()
        ));
        Assert.assertEquals(expected, differentFiles.list());
    }

    private static class ModuleFacade extends AbstractModule {
        private final Module module;
        private Path workDir;

        public ModuleFacade(Path workDir) {
            this.module = new Module(null);
            this.workDir = workDir;
        }

        @Singleton @Provides public Git provideGit(Path workDir) throws IOException, GitAPIException {
            return module.provideGit(workDir);
        }

        @Singleton @Provides public Path workDir() {
            System.setProperty("user.dir", workDir.toString());
            return workDir;
        }

        @Singleton @Provides public Arguments arguments(Path workDir) throws IOException {
            return new Arguments(new String[] {"."}, workDir);
        }

        @Override
        protected void configure() {}
    }
}

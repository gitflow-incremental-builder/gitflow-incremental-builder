package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.boundary.Module;
import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ChangedModulesTest extends RepoTest {

    private static final String USER_DIR = "user.dir";

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4")
        ));
        final Set<Path> actual = Guice.createInjector(new Module(new ConsoleLogger(), MavenSessionMock.get()))
                .getInstance(ChangedModules.class).set().stream()
                .map(MavenProject::getBasedir).map(File::toPath).map(LocalRepoMock.WORK_DIR.resolve("parent")::relativize).collect(Collectors.toSet());
        Assert.assertEquals(expected, actual);
    }

    private MavenSession getMavenSessionMock(Path pom) {
        MavenSession mavenSession = Mockito.mock(MavenSession.class);
        MavenProject mavenProject = Mockito.mock(MavenProject.class);
        Mockito.when(mavenProject.getFile()).thenReturn(pom.toFile());
        Mockito.when(mavenSession.getCurrentProject()).thenReturn(mavenProject);
        return mavenSession;
    }
}

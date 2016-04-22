package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.boundary.Module;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChangedModulesTest extends RepoTest {

    private static final String USER_DIR = "user.dir";

    @Test
    public void list() throws Exception {
        final Path pom = Paths.get(System.getProperty(USER_DIR)).resolve("parent/pom.xml");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4")
        ));
        final Set<Path> actual = Guice.createInjector(new Module(new ConsoleLogger(), getMavenSessionMock(pom))).getInstance(ChangedModules.class).list(pom);
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

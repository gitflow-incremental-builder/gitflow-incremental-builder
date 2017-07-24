package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.boundary.GuiceModule;
import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ChangedProjectsTest extends BaseRepoTest {

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4"),
                Paths.get("testJarDependent")
        ));
        final Set<Path> actual = Guice.createInjector(new GuiceModule(new ConsoleLogger(), getMavenSessionMock()))
                .getInstance(ChangedProjects.class).get().stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getBaseCanonicalBaseFolder().toPath().resolve("parent")::relativize)
                .collect(Collectors.toSet());
        Assert.assertEquals(expected, actual);
    }

}

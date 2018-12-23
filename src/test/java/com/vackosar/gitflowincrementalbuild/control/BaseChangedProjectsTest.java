package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for testing {@link ChangedProjects}. This class does <em>not</em> isolate {@link ChangedProjects}, it rather combines
 * {@link ChangedProjects}, {@link DifferentFiles} and {@link Modules} which is actually too much for a focused unit test and should be
 * cleaned up. This test should thus be considered a "small scale integration test".
 *
 * @author famod
 *
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class BaseChangedProjectsTest extends BaseRepoTest {

    @Mock
    private Logger loggerMock;

    @Spy
    @InjectMocks    // note: this won't populate mavenSession because the mock for this is created later via getMavenSessionMock()
    protected DifferentFiles differentFilesSpy;

    @Spy
    protected Modules modulesSpy;

    @InjectMocks
    protected ChangedProjects underTest;

    private MavenSession mavenSessionMock;

    public BaseChangedProjectsTest(boolean useSymLinkedFolder) {
        super(useSymLinkedFolder, /* withRemote */ false);
    }

    @Before
    public void injectMavenSessionMock() throws Exception {
        mavenSessionMock = getMavenSessionMock();
        Whitebox.setInternalState(differentFilesSpy, mavenSessionMock, new Configuration.Provider(mavenSessionMock));
        Whitebox.setInternalState(underTest, mavenSessionMock);
    }

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4"),
                Paths.get("testJarDependent")
        ));

        final Set<Path> actual = underTest.get().stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getBaseCanonicalBaseFolder().toPath().resolve("parent")::relativize)
                .collect(Collectors.toSet());

        Assert.assertEquals(expected, actual);
    }

}

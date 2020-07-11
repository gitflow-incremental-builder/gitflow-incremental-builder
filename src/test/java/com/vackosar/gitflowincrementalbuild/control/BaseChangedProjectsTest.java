package com.vackosar.gitflowincrementalbuild.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
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
@ExtendWith(MockitoExtension.class)
public abstract class BaseChangedProjectsTest extends BaseRepoTest {

    @Spy
    @InjectMocks    // note: this won't populate mavenSession because the mock for this is created later via getMavenSessionMock()
    protected DifferentFiles differentFilesSpy;

    @Spy
    protected Modules modulesSpy;

    @InjectMocks
    protected ChangedProjects underTest;

    private MavenSession mavenSessionMock;
    private GitProvider gitProvider;

    public BaseChangedProjectsTest(boolean useSymLinkedFolder) {
        super(useSymLinkedFolder, /* remoteRepoServerType */ null);
    }

    @BeforeEach
    void injectMavenSessionMockAndGitProvider() throws Exception {
        mavenSessionMock = getMavenSessionMock();
        Configuration.Provider configProvider = new Configuration.Provider(mavenSessionMock);
        gitProvider = new GitProvider(mavenSessionMock, configProvider.get());
        Whitebox.setInternalState(differentFilesSpy, configProvider, gitProvider);
        Whitebox.setInternalState(underTest, mavenSessionMock);
    }

    @AfterEach
    void closeGitProvider() {
        if (gitProvider != null) {
            gitProvider.close();
        }
    }

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("parent/child2/subchild2"),
                Paths.get("parent/child3"),
                Paths.get("parent/child4"),
                Paths.get("parent/testJarDependent")
        ));

        final Set<Path> actual = underTest.get().stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getBaseCanonicalBaseFolder().toPath()::relativize)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    @Test
    public void list_ignoreChangedNonReactorModule() throws Exception {
        // remove child3 (which contains changes) from the reactor/session
        mavenSessionMock.getAllProjects().removeIf(proj -> proj.getArtifactId().equals("child3"));
        mavenSessionMock.getProjects().removeIf(proj -> proj.getArtifactId().equals("child3"));

        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("parent/child2/subchild2"),
                Paths.get("parent/child4"),
                Paths.get("parent/testJarDependent")
        ));

        final Set<Path> actual = underTest.get().stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getBaseCanonicalBaseFolder().toPath()::relativize)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }
}

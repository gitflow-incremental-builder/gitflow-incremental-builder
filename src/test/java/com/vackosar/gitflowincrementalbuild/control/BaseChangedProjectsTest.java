package com.vackosar.gitflowincrementalbuild.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;

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
        gitProvider = new GitProvider();
        Whitebox.setInternalState(differentFilesSpy, gitProvider);
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

        final Set<Path> actual = underTest.get(config()).stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getRepoDir()::relativize)
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
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

        final Set<Path> actual = underTest.get(config()).stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getRepoDir()::relativize)
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);
    }

    protected Configuration config() {
        return new Configuration(mavenSessionMock);
    }
}

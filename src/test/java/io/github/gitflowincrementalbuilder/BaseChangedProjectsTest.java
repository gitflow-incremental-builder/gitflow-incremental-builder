package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.jgit.DifferentFiles;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

/**
 * Base class for testing {@link ChangedProjects}. This class does <em>not</em> isolate {@link ChangedProjects}, it rather combines
 * {@link ChangedProjects}, {@link DifferentFiles} and {@link Modules} which is actually too much for a focused unit test and should be
 * cleaned up. This test should thus be considered a "small scale integration test".
 *
 * @author famod
 *
 */
@ExtendWith(MockitoExtension.class)
abstract class BaseChangedProjectsTest extends BaseRepoTest {

    @InjectMocks
    protected DifferentFiles differentFilesSpy = spy(DifferentFiles.class);

    @Spy
    protected Modules modulesSpy;

    @InjectMocks
    protected ChangedProjects underTest;

    @Spy
    private GitProvider gitProviderSpy;

    private MavenSession mavenSessionMock;

    public BaseChangedProjectsTest(boolean useSymLinkedFolder) {
        super(useSymLinkedFolder, "src");   // src for #446
    }

    @BeforeEach
    void initMavenSessionMock() throws Exception {
        mavenSessionMock = getMavenSessionMock();
    }

    @AfterEach
    void closeGitProvider() {
        if (gitProviderSpy != null) {
            gitProviderSpy.close();
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

        final List<MavenProject> projects = assertExpectedProjectsFound(expected);
        assertThat(projects).noneMatch(project -> project.getContextValue(ChangedProjects.CTX_TEST_ONLY) == Boolean.TRUE);
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

        final List<MavenProject> projects = assertExpectedProjectsFound(expected);
        assertThat(projects).noneMatch(project -> project.getContextValue(ChangedProjects.CTX_TEST_ONLY) == Boolean.TRUE);
    }

    @Test
    public void list_testOnly() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.prefixedName(), "true");
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");
        Path testJavaPath = Files.createDirectories(localRepoMock.getRepoDir().resolve("parent/child6/src/test/java"));
        Files.createFile(testJavaPath.resolve("FooTest.java"));

        final Set<Path> expected = Collections.singleton(Paths.get("parent/child6"));

        MavenProject project = assertExpectedProjectsFound(expected).get(0);
        assertThat(project.getContextValue(ChangedProjects.CTX_TEST_ONLY)).isSameAs(Boolean.TRUE);

        Path mainJavaPath = Files.createDirectories(localRepoMock.getRepoDir().resolve("parent/child6/src/main/java"));
        Files.createFile(mainJavaPath.resolve("Foo.java"));

        project = assertExpectedProjectsFound(expected).get(0);
        assertThat(project.getContextValue(ChangedProjects.CTX_TEST_ONLY)).isSameAs(Boolean.FALSE);
    }

    @Test
    public void embeddedTestMavenProject() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.prefixedName(), "true");
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");
        Path testProjectPath = Files.createDirectories(localRepoMock.getRepoDir().resolve("parent/child6/src/test/resources/project"));
        Files.createFile(testProjectPath.resolve("pom.xml"));

        final Set<Path> expected = Collections.singleton(Paths.get("parent/child6"));

        assertExpectedProjectsFound(expected);
    }

    @Test
    public void foo() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.prefixedName(), "true");
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");
        Path testProjectPath = Files.createDirectories(localRepoMock.getRepoDir().resolve("src/java"));
        Files.createFile(testProjectPath.resolve("pom.xml"));

        final Set<Path> expected = Collections.emptySet();

        assertExpectedProjectsFound(expected);
    }

    private List<MavenProject> assertExpectedProjectsFound(final Set<Path> expected) {
        Set<MavenProject> foundProjects = underTest.get(config());
        final Set<Path> actual = underTest.get(config()).stream()
                .map(MavenProject::getBasedir)
                    .map(File::toPath)
                    .map(localRepoMock.getRepoDir()::relativize)
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(expected);

        return new ArrayList<>(foundProjects);
    }

    protected Configuration config() {
        return new Configuration(mavenSessionMock);
    }
}

package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;

/**
 * Tests {@link ImpactedDependencies}.
 */
@ExtendWith(MockitoExtension.class)
class ImpactedDependenciesTest {

    private static final Path PSEUDO_PROJECT_ROOT = Paths.get(".").toAbsolutePath();

    @TempDir
    Path tempDir;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private MavenSession mavenSessionMock;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private MavenExecutionRequest mavenExecutionRequestMock;

    private ImpactedDependencies underTest;

    private Path impactedDepsFile;
    private List<MavenProject> projects;
    private MavenProject currentProject;

    @BeforeEach
    void setUp() {
        underTest = new ImpactedDependencies();
        impactedDepsFile = tempDir.resolve("impacted-deps.txt");
        projects = new ArrayList<>();
        
        // Setup current project with properties
        currentProject = createProject("current-project");
        projects.add(currentProject);  // Add current project to projects list
        
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getCurrentProject()).thenReturn(currentProject);
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        when(mavenExecutionRequestMock.isRecursive()).thenReturn(true);
        when(mavenSessionMock.getGoals()).thenReturn(new ArrayList<>());
    }

    @Test
    void emptyOptional_returnsEmptySet() {
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void fileDoesNotExist_returnsEmptySet() {
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyFile_returnsEmptySet() throws IOException {
        Files.createFile(impactedDepsFile);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void fileWithOnlyWhitespace_returnsEmptySet() throws IOException {
        Files.write(impactedDepsFile, Arrays.asList("", "   ", "\t"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void fileWithOnlyComments_returnsEmptySet() throws IOException {
        Files.write(impactedDepsFile, Arrays.asList(
                "# This is a comment",
                "# Another comment",
                "#com.example:lib:1.0"
        ), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void singleImpactedGAV_matchingDirectDependency() throws IOException {
        MavenProject project = createProject("test-project");
        Dependency dep = createDependency("com.example", "lib", "1.0");
        project.getModel().setDependencies(Arrays.asList(dep));
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactly(project);
    }

    @Test
    void singleImpactedGAV_matchingTransitiveDependency() throws IOException {
        MavenProject project = createProject("test-project");
        Artifact artifact = createArtifact("com.example", "lib", "1.0");
        project.getArtifacts().add(artifact);
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactly(project);
    }

    @Test
    void singleImpactedGAV_noMatchingDependency() throws IOException {
        MavenProject project = createProject("test-project");
        Artifact artifact = createArtifact("com.other", "lib", "2.0");
        project.getArtifacts().add(artifact);
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void multipleImpactedGAVs_multipleMatchingProjects() throws IOException {
        MavenProject project1 = createProject("project1");
        project1.getArtifacts().add(createArtifact("com.example", "lib1", "1.0"));
        projects.add(project1);

        MavenProject project2 = createProject("project2");
        project2.getArtifacts().add(createArtifact("com.example", "lib2", "2.0"));
        projects.add(project2);

        MavenProject project3 = createProject("project3");
        project3.getArtifacts().add(createArtifact("com.other", "lib3", "3.0"));
        projects.add(project3);

        Files.write(impactedDepsFile, Arrays.asList(
                "com.example:lib1:1.0",
                "com.example:lib2:2.0"
        ), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactlyInAnyOrder(project1, project2);
    }

    @Test
    void projectWithMultipleDependencies_oneMatches() throws IOException {
        MavenProject project = createProject("test-project");
        project.getArtifacts().add(createArtifact("com.example", "lib1", "1.0"));
        project.getArtifacts().add(createArtifact("com.example", "lib2", "2.0"));
        project.getArtifacts().add(createArtifact("com.other", "lib3", "3.0"));
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib2:2.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactly(project);
    }

    @Test
    void fileWithCommentsAndEmptyLines() throws IOException {
        MavenProject project = createProject("test-project");
        project.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList(
                "# Comment at the beginning",
                "",
                "com.example:lib:1.0",
                "   ",
                "# Comment in the middle",
                ""
        ), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactly(project);
    }

    @Test
    void projectWithNullDependencies_doesNotMatch() throws IOException {
        MavenProject project = createProject("test-project");
        project.getModel().setDependencies(null);
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void projectWithNullArtifacts_doesNotMatch() throws IOException {
        MavenProject project = Mockito.mock(MavenProject.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(project.getId()).thenReturn("test:project:1.0");
        when(project.getModel()).thenReturn(new Model());
        when(project.getArtifacts()).thenReturn(null);
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void multipleProjectsWithSameDependency_bothIncluded() throws IOException {
        MavenProject project1 = createProject("project1");
        project1.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));
        projects.add(project1);

        MavenProject project2 = createProject("project2");
        project2.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));
        projects.add(project2);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactlyInAnyOrder(project1, project2);
    }

    @Test
    void fileWithDuplicateGAVs_handledCorrectly() throws IOException {
        MavenProject project = createProject("test-project");
        project.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList(
                "com.example:lib:1.0",
                "com.example:lib:1.0",
                "com.example:lib:1.0"
        ), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).containsExactly(project);
    }

    @Test
    void unreadableFile_throwsException() throws IOException {
        Path unreadableFile = tempDir.resolve("unreadable.txt");
        Files.createFile(unreadableFile);
        // Create a directory with the same name to make it unreadable as a regular file
        Files.delete(unreadableFile);
        Files.createDirectory(unreadableFile);
        
        setImpactedDependenciesFromProperty(unreadableFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        assertThatThrownBy(() -> underTest.get(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read impacted dependencies");
    }

    @Test
    void projectWithEmptyArtifactsSet_doesNotMatch() throws IOException {
        MavenProject project = createProject("test-project");
        // Artifacts set is empty (not null)
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    @Test
    void gavWithDifferentVersion_doesNotMatch() throws IOException {
        MavenProject project = createProject("test-project");
        project.getArtifacts().add(createArtifact("com.example", "lib", "2.0"));
        projects.add(project);

        Files.write(impactedDepsFile, Arrays.asList("com.example:lib:1.0"), StandardCharsets.UTF_8);
        setImpactedDependenciesFromProperty(impactedDepsFile.toAbsolutePath().toString());
        Configuration config = new Configuration(mavenSessionMock);

        Set<MavenProject> result = underTest.get(config);

        assertThat(result).isEmpty();
    }

    // Helper methods

    private void setImpactedDependenciesFromProperty(String filePath) {
        currentProject.getProperties().put(Property.loadImpactedDependenciesFrom.prefixedName(), filePath);
    }

    private MavenProject createProject(String artifactId) {
        MavenProject project = new MavenProject();
        project.setGroupId("io.github.gitflow-incremental-builder");
        project.setArtifactId(artifactId);
        project.setVersion("1.0.0");
        project.setArtifacts(new HashSet<>());
        project.setModel(new Model());
        project.getModel().setGroupId("io.github.gitflow-incremental-builder");
        project.getModel().setArtifactId(artifactId);
        project.getModel().setVersion("1.0.0");
        project.getModel().setDependencies(new ArrayList<>());
        project.setFile(new File(PSEUDO_PROJECT_ROOT.resolve(artifactId).toFile(), "pom.xml"));
        return project;
    }

    private Dependency createDependency(String groupId, String artifactId, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(groupId);
        dep.setArtifactId(artifactId);
        dep.setVersion(version);
        return dep;
    }

    private Artifact createArtifact(String groupId, String artifactId, String version) {
        return new DefaultArtifact(groupId, artifactId, version, "compile", "jar", null, new DefaultArtifactHandler());
    }
}

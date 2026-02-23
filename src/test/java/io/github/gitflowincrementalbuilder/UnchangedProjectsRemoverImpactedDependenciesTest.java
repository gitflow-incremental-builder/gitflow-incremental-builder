package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of {@link Property#impactedDependenciesFrom}.
 *
 * @author famod
 */
public class UnchangedProjectsRemoverImpactedDependenciesTest extends BaseUnchangedProjectsRemoverTest {

    @TempDir
    Path tempDir;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private GitProvider gitProviderMock;

    private Path impactedDepsFile;

    @BeforeEach
    void beforeThis() {
        impactedDepsFile = tempDir.resolve("impacted-deps.txt");
        when(gitProviderMock.getProjectRoot(any(Configuration.class))).thenReturn(PSEUDO_PROJECT_ROOT);
    }

    @Test
    public void noImpactedDependenciesFile() throws IOException {
        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // File doesn't exist, so no changes should be detected (should act as if nothing changed)
        // This should result in building just the current project
        assertThat(config().mavenSession.getProjects()).hasSize(1);
    }

    @Test
    public void emptyImpactedDependenciesFile() throws IOException {
        // Create empty file
        Files.createFile(impactedDepsFile);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // Empty file means no dependencies impact anything
        assertThat(config().mavenSession.getProjects()).hasSize(1);
    }

    @Test
    public void projectWithMatchingTransitiveDependency() throws IOException {
        MavenProject moduleA = this.moduleA;
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);
        MavenProject moduleC = addModuleMock(AID_MODULE_C, false);

        // moduleB has a transitive dependency to external-lib:1.0
        String externalGav = "com.example:external-lib:1.0";
        Artifact artifact = createArtifact("com.example", "external-lib", "1.0");
        moduleB.getArtifacts().add(artifact);

        // Write the impacted dependencies file
        Files.write(impactedDepsFile, Arrays.asList(externalGav), StandardCharsets.UTF_8);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // moduleB should be included because it has the external dependency
        assertThat(config().mavenSession.getProjects()).contains(moduleB);
    }

    @Test
    public void multipleProjectsWithDependencies() throws IOException {
        MavenProject moduleA = this.moduleA;
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);
        MavenProject moduleC = addModuleMock(AID_MODULE_C, false);
        MavenProject moduleD = addModuleMock(AID_MODULE_D, false);

        // moduleB and moduleD have transitive dependencies to impacted GAVs
        String impactedGav1 = "com.example:lib1:1.0";
        String impactedGav2 = "com.example:lib2:2.0";

        moduleB.getArtifacts().add(createArtifact("com.example", "lib1", "1.0"));
        moduleD.getArtifacts().add(createArtifact("com.example", "lib2", "2.0"));

        // moduleC is not affected

        // Write the impacted dependencies file with multiple GAVs
        Files.write(impactedDepsFile, Arrays.asList(impactedGav1, impactedGav2), StandardCharsets.UTF_8);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // moduleB and moduleD should be included
        assertThat(config().mavenSession.getProjects()).contains(moduleB, moduleD);
    }

    @Test
    public void ignoreCommentLines() throws IOException {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);

        String impactedGav = "com.example:lib:1.0";
        moduleB.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));

        // Write file with comments
        Files.write(impactedDepsFile, Arrays.asList(
                "# This is a comment",
                impactedGav,
                "# Another comment",
                ""
        ), StandardCharsets.UTF_8);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // moduleB should be included despite comments
        assertThat(config().mavenSession.getProjects()).contains(moduleB);
    }

    @Test
    public void impactedDependenciesWithDownstream() throws IOException {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);
        MavenProject moduleC = addModuleMock(AID_MODULE_C, false);

        // moduleB has a transitive dependency to impacted GAV
        String impactedGav = "com.example:lib:1.0";
        moduleB.getArtifacts().add(createArtifact("com.example", "lib", "1.0"));

        // moduleC depends on moduleB (downstream)
        setDownstreamProjectsNonTransitive(moduleB, moduleC);

        Files.write(impactedDepsFile, Arrays.asList(impactedGav), StandardCharsets.UTF_8);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // Both moduleB (impacted) and moduleC (downstream) should be included
        assertThat(config().mavenSession.getProjects()).contains(moduleB, moduleC);
    }

    @Test
    public void noProjectsWithMatchingDependencies() throws IOException {
        // Write impacted dependencies file with GAVs that no project has
        Files.write(impactedDepsFile, Arrays.asList("com.example:unknown:1.0"), StandardCharsets.UTF_8);

        addGibProperty(Property.impactedDependenciesFrom, impactedDepsFile.toAbsolutePath().toString());

        underTest.act(config());

        // No projects should be built since nothing matches the dependencies
        assertThat(config().mavenSession.getProjects()).hasSize(1); // Only root
    }

    private Artifact createArtifact(String groupId, String artifactId, String version) {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, version, "compile", "jar", null, new DefaultArtifactHandler());
        return artifact;
    }
}


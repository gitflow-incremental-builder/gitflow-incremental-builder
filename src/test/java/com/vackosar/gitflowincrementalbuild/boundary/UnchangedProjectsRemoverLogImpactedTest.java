package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of {@link Property#logImpactedTo}.
 *
 * @author famod
 */
public class UnchangedProjectsRemoverLogImpactedTest extends BaseUnchangedProjectsRemoverTest {

    @TempDir
    Path tempDir;

    @Mock(lenient = true)
    private GitProvider gitProviderMock;

    private Path logFilePath;

    @BeforeEach
    void beforeThis() {
        logFilePath = tempDir.resolve("impacted.log");
        addGibProperty(Property.logImpactedTo, logFilePath.toAbsolutePath().toString());

        when(gitProviderMock.getProjectRoot(any(Configuration.class))).thenReturn(PSEUDO_PROJECT_ROOT);
    }

    @Test
    public void logImpatcedTo_nothingChanged() throws IOException {
        addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertLogFileContains(logFilePath);
    }

    @Test
    public void singleChanged() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertLogFileContains(logFilePath, changedModuleMock);
    }

    @Test
    public void singleChanged_withDownstream() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_C, false);
        MavenProject independentModuleMock = addModuleMock(AID_MODULE_D, false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);
        setUpstreamProjects(independentModuleMock, moduleA);

        underTest.act(config());

        assertLogFileContains(logFilePath, changedModuleMock, dependentModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.buildUpstream, "true");

        underTest.act(config());

        assertLogFileContains(logFilePath, changedModuleMock);
    }

    @Test
    public void logImpactedNonExistingPath() throws IOException {
        Path nonExistingPath = Paths.get("some", "unknown", "path", "impacted.log");
        Path customLogFilePath = tempDir.resolve(nonExistingPath);
        assertThat(!Files.exists(customLogFilePath));

        addGibProperty(Property.logImpactedTo, customLogFilePath.toAbsolutePath().toString());

        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertThat(Files.exists(customLogFilePath));
        assertLogFileContains(customLogFilePath, changedModuleMock);
    }

    private void assertLogFileContains(Path logFilePath, MavenProject... mavenProjects) throws IOException {
        assertThat(Files.isReadable(logFilePath))
                .as(logFilePath + " is missing")
                .isTrue();
        assertThat(Files.readAllLines(logFilePath))
                .as("Unexpected content of " + logFilePath)
                .isEqualTo(Arrays.stream(mavenProjects).map(proj -> proj.getBasedir().getName()).collect(Collectors.toList()));
    }
}

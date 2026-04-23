package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of {@link Property#logImpactedTo}
 * and {@link Property#logImpactedGavTo}.
 */
public class UnchangedProjectsRemoverLogImpactedTest extends BaseUnchangedProjectsRemoverTest {

    @TempDir
    Path tempDir;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private GitProvider gitProviderMock;

    private Path logFilePath;

    @BeforeEach
    void beforeThis() {
        logFilePath = tempDir.resolve("impacted.log");
        addGibProperty(Property.logImpactedTo, logFilePath.toAbsolutePath().toString());

        when(gitProviderMock.getProjectRoot(any(Configuration.class))).thenReturn(PSEUDO_PROJECT_ROOT);
    }

    @Test
    public void nothingChanged() throws IOException {
        addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertPathLogFileContains(logFilePath);
    }

    @Test
    public void singleChanged() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertPathLogFileContains(logFilePath, changedModuleMock);
    }

    @Test
    public void singleChanged_withDownstream() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_C, false);
        MavenProject independentModuleMock = addModuleMock(AID_MODULE_D, false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);
        setUpstreamProjects(independentModuleMock, moduleA);

        underTest.act(config());

        assertPathLogFileContains(logFilePath, changedModuleMock, dependentModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream() throws IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.buildUpstream, "true");

        underTest.act(config());

        assertPathLogFileContains(logFilePath, changedModuleMock);
    }

    @Test
    public void skipExecutionException() throws IOException {
        addModuleMock(AID_MODULE_B, true);
        Files.createFile(logFilePath);
        Configuration config = config();
        when(changedProjectsMock.get(config)).thenThrow(new SkipExecutionException("deliberate test exception"));

        assertThatThrownBy(() -> underTest.act(config)).isInstanceOf(SkipExecutionException.class);

        assertThat(logFilePath).doesNotExist();
    }

    @Test
    public void onlySelectedModulesPresent() throws IOException {
        addModuleMock(AID_MODULE_B, true);
        setProjectSelections(moduleA);
        overrideProjects(moduleA);

        underTest.act(config());

        assertPathLogFileContains(logFilePath, moduleA);
    }

    @Test
    public void nonRecursive() throws IOException {
        addModuleMock(AID_MODULE_B, true);
        when(mavenExecutionRequestMock.isRecursive()).thenReturn(false);

        underTest.act(config());

        assertPathLogFileContains(logFilePath, moduleA);
    }

    @Test
    public void logImpactedNonExistingPath() throws IOException {
        Path nonExistingPath = Path.of("some", "unknown", "path", "impacted.log");
        Path customLogFilePath = tempDir.resolve(nonExistingPath);
        assertThat(!Files.exists(customLogFilePath));

        addGibProperty(Property.logImpactedTo, customLogFilePath.toAbsolutePath().toString());

        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertThat(Files.exists(customLogFilePath));
        assertPathLogFileContains(customLogFilePath, changedModuleMock);
    }

    @Nested
    class LogImpactedGavTo {

        private Path gavLogFilePath;

        @BeforeEach
        void beforeGav() {
            gavLogFilePath = tempDir.resolve("impacted-gavs.log");
            addGibProperty(Property.logImpactedGavTo, gavLogFilePath.toAbsolutePath().toString());
        }

        @Test
        public void nothingChanged() throws IOException {
            addModuleMock(AID_MODULE_B, false);

            underTest.act(config());

            assertPathLogFileContains(logFilePath);
            assertGavLogFileContains(gavLogFilePath);
        }

        @Test
        public void singleChanged() throws IOException {
            MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

            underTest.act(config());

            assertPathLogFileContains(logFilePath, changedModuleMock);
            assertGavLogFileContains(gavLogFilePath, changedModuleMock);
        }

        @Test
        public void singleChanged_withDownstream() throws IOException {
            MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
            MavenProject dependentModuleMock = addModuleMock(AID_MODULE_C, false);
            MavenProject independentModuleMock = addModuleMock(AID_MODULE_D, false);

            setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
            setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);
            setUpstreamProjects(independentModuleMock, moduleA);

            underTest.act(config());

            assertPathLogFileContains(logFilePath, changedModuleMock, dependentModuleMock);
            assertGavLogFileContains(gavLogFilePath, changedModuleMock, dependentModuleMock);
        }

        @Test
        public void onlySelectedModulesPresent() throws IOException {
            addModuleMock(AID_MODULE_B, true);
            setProjectSelections(moduleA);
            overrideProjects(moduleA);

            underTest.act(config());

            assertPathLogFileContains(logFilePath, moduleA);
            assertGavLogFileContains(gavLogFilePath, moduleA);
        }

        @Test
        public void nonRecursive() throws IOException {
            addModuleMock(AID_MODULE_B, true);
            when(mavenExecutionRequestMock.isRecursive()).thenReturn(false);

            underTest.act(config());

            assertPathLogFileContains(logFilePath, moduleA);
            assertGavLogFileContains(gavLogFilePath, moduleA);
        }

        @Test
        public void skipExecutionException() throws IOException {
            addModuleMock(AID_MODULE_B, true);
            Files.createFile(logFilePath);
            Files.createFile(gavLogFilePath);
            Configuration config = config();
            when(changedProjectsMock.get(config)).thenThrow(new SkipExecutionException("deliberate test exception"));

            assertThatThrownBy(() -> underTest.act(config)).isInstanceOf(SkipExecutionException.class);

            assertThat(logFilePath).doesNotExist();
            assertThat(gavLogFilePath).doesNotExist();
        }

        @Test
        public void gavOnly() throws IOException {
            addGibProperty(Property.logImpactedTo, "");
            MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

            underTest.act(config());

            assertThat(logFilePath).doesNotExist();
            assertGavLogFileContains(gavLogFilePath, changedModuleMock);
        }

        @Test
        public void nonExistingPath() throws IOException {
            Path nonExistingPath = Path.of("some", "unknown", "path", "impacted-gavs.log");
            Path customGavLogFilePath = tempDir.resolve(nonExistingPath);
            assertThat(!Files.exists(customGavLogFilePath));

            addGibProperty(Property.logImpactedGavTo, customGavLogFilePath.toAbsolutePath().toString());

            MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

            underTest.act(config());

            assertThat(Files.exists(customGavLogFilePath));
            assertGavLogFileContains(customGavLogFilePath, changedModuleMock);
        }
    }

    private void assertPathLogFileContains(Path logFilePath, MavenProject... mavenProjects) throws IOException {
        assertThat(Files.isReadable(logFilePath))
                .as(logFilePath + " is missing")
                .isTrue();

        List<String> expected = Arrays.stream(mavenProjects)
                .map(proj -> proj.getBasedir().getName())
                .collect(Collectors.toList());

        assertThat(Files.readAllLines(logFilePath))
                .as("Unexpected content of " + logFilePath)
                .isEqualTo(expected);
    }

    private void assertGavLogFileContains(Path logFilePath, MavenProject... mavenProjects) throws IOException {
        assertThat(Files.isReadable(logFilePath))
                .as(logFilePath + " is missing")
                .isTrue();

        List<String> expected = Arrays.stream(mavenProjects)
                .map(proj -> proj.getGroupId() + ":" + proj.getArtifactId() + ":" + proj.getVersion())
                .collect(Collectors.toList());

        assertThat(Files.readAllLines(logFilePath))
                .as("Unexpected content of " + logFilePath)
                .isEqualTo(expected);
    }
}

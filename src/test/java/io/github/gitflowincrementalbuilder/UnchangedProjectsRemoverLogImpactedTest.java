package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Configuration.LogImpactedFormat;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of {@link Property#logImpactedTo}.
 *
 * @author famod
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

    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void logImpatcedTo_nothingChanged(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertLogFileContains(logFilePath, format);
    }

    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void singleChanged(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertLogFileContains(logFilePath, format, changedModuleMock);
    }

    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void singleChanged_withDownstream(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_C, false);
        MavenProject independentModuleMock = addModuleMock(AID_MODULE_D, false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);
        setUpstreamProjects(independentModuleMock, moduleA);

        underTest.act(config());

        assertLogFileContains(logFilePath, format, changedModuleMock, dependentModuleMock);
    }

    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void singleChanged_buildUpstream(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.buildUpstream, "true");

        underTest.act(config());

        assertLogFileContains(logFilePath, format, changedModuleMock);
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


    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void onlySelectedModulesPresent(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        addModuleMock(AID_MODULE_B, true);
        setProjectSelections(moduleA);
        overrideProjects(moduleA);

        underTest.act(config());

        assertLogFileContains(logFilePath, format, moduleA);
    }

    @ParameterizedTest
    @EnumSource(LogImpactedFormat.class)
    public void nonRecursive(LogImpactedFormat format) throws IOException {
        addGibProperty(Property.logImpactedFormat, format.name().toLowerCase());
        addModuleMock(AID_MODULE_B, true);
        when(mavenExecutionRequestMock.isRecursive()).thenReturn(false);

        underTest.act(config());

        assertLogFileContains(logFilePath, format, moduleA);
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
        assertLogFileContains(customLogFilePath, LogImpactedFormat.PATH, changedModuleMock);
    }

    private void assertLogFileContains(Path logFilePath, LogImpactedFormat format, MavenProject... mavenProjects) throws IOException {
        assertThat(Files.isReadable(logFilePath))
                .as(logFilePath + " is missing")
                .isTrue();

        assertThat(Files.readAllLines(logFilePath))
                .as("Unexpected content of " + logFilePath)
                .isEqualTo(formatProjects(format, mavenProjects));
    }

    private List<String> formatProjects(LogImpactedFormat format, MavenProject... mavenProjects) {
        switch (format) {
            case GAV:
                return Arrays.stream(mavenProjects)
                        .map(proj -> proj.getGroupId() + ":" + proj.getArtifactId() + ":" + proj.getVersion())
                        .collect(Collectors.toList());
            case PATH:
                return Arrays.stream(mavenProjects)
                        .map(proj -> proj.getBasedir().getName())
                        .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("Unsupported LogImpactedFormat: " + format);
        }
    }
}

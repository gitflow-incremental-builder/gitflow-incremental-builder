package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
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

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private GitProvider gitProviderMock;

    private Path logFilePath;

    @BeforeEach
    void beforeThis() throws GitAPIException, IOException {
        logFilePath = tempDir.resolve("impacted.log");
        addGibProperty(Property.logImpactedTo, logFilePath.toAbsolutePath().toString());

        when(gitProviderMock.get(any(Configuration.class)).getRepository().getDirectory())
                .thenReturn(PSEUDO_PROJECT_ROOT.resolve(".git").toFile());
    }

    @Test
    public void logImpatcedTo_nothingChanged() throws GitAPIException, IOException {
        addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertLogFileContains();
    }

    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        assertLogFileContains(changedModuleMock);
    }

    @Test
    public void singleChanged_withDownstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_C, false);
        MavenProject independentModuleMock = addModuleMock(AID_MODULE_D, false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);
        setUpstreamProjects(independentModuleMock, moduleA);

        underTest.act(config());

        assertLogFileContains(changedModuleMock, dependentModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.buildUpstream, "true");

        underTest.act(config());

        assertLogFileContains(changedModuleMock);
    }

    private void assertLogFileContains(MavenProject... mavenProjects) throws IOException {
        assertThat(Files.isReadable(logFilePath))
                .as(logFilePath + " is missing")
                .isTrue();
        assertThat(Files.readAllLines(logFilePath))
                .as("Unexpected content of " + logFilePath)
                .isEqualTo(Arrays.stream(mavenProjects).map(proj -> proj.getBasedir().getName()).collect(Collectors.toList()));
    }
}

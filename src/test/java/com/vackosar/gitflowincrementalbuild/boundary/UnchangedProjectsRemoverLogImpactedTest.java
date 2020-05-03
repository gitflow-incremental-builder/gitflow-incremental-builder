package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of {@link Property#logImpactedTo}.
 *
 * @author famod
 */
public class UnchangedProjectsRemoverLogImpactedTest extends BaseUnchangedProjectsRemoverTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path logFilePath;

    @Override
    @Before
    public void before() throws GitAPIException, IOException {
        super.before();

        logFilePath = temporaryFolder.getRoot().toPath().resolve("impacted.log");
        addGibProperty(Property.logImpactedTo, logFilePath.toAbsolutePath().toString());
    }

    @Test
    public void logImpatcedTo_nothingChanged() throws GitAPIException, IOException {
        addModuleMock(AID_MODULE_B, false);

        underTest.act();

        assertLogFileContains();
    }

    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act();

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

        underTest.act();

        assertLogFileContains(changedModuleMock, dependentModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.buildUpstream, "true");

        underTest.act();

        assertLogFileContains(changedModuleMock);
    }

    private void assertLogFileContains(MavenProject... mavenProjects) throws IOException {
        assertTrue(logFilePath + " is missing", Files.isReadable(logFilePath));
        assertEquals("Unexpected content of " + logFilePath,
                Arrays.stream(mavenProjects).map(proj -> proj.getBasedir().getPath()).collect(Collectors.toList()),
                Files.readAllLines(logFilePath));
    }
}

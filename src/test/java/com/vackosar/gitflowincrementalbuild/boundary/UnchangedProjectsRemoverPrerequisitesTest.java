package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;

import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 * Verifies the behaviour where prerequisite modules are specified.
 *
 * @author harrisric
 */
public class UnchangedProjectsRemoverPrerequisitesTest extends BaseUnchangedProjectsRemoverTest {


    @Test
    public void nothingChanged() throws GitAPIException, IOException {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);
        MavenProject moduleC = addModuleMock(AID_MODULE_C, false);
        addGibProperty(Property.prerequisiteModules, AID_MODULE_B+"="+AID_MODULE_C);

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goal").isEqualTo(Collections.singletonList("validate"));

        verify(mavenSessionMock).setProjects(Collections.singletonList(moduleA));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(moduleB);
        assertProjectPropertiesEqual(moduleC);
    }

    // linear: A <- B <- C <- D
    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules_linear() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedIntermediateModuleMock = addModuleMock(AID_MODULE_C, false);
        MavenProject dependsOnIntermediateModuleMock = addModuleMock(AID_MODULE_D, true);
        MavenProject prerequisiteE = addModuleMock(AID_MODULE_E, false);
        MavenProject prerequisiteF = addModuleMock("module-F", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpstreamProjects(dependsOnIntermediateModuleMock, unchangedIntermediateModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);
        setDownstreamProjects(unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);

        addGibProperty(Property.buildUpstreamMode, "changed");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");
        addGibProperty(Property.prerequisiteModules, AID_MODULE_C+"=module-F "+AID_MODULE_B+"="+AID_MODULE_E);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock, prerequisiteE, prerequisiteF));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedIntermediateModuleMock);
        assertProjectPropertiesEqual(dependsOnIntermediateModuleMock);
        assertProjectPropertiesEqual(prerequisiteE);
    }


    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules_prerequisites() throws GitAPIException, IOException {
        MavenProject changedModuleMockWithPrerequisites = addModuleMock(AID_MODULE_B, true);
        MavenProject changedModuleMockNoPrerequisites = addModuleMock(AID_MODULE_C, true);
        MavenProject prerequisiteD = addModuleMock(AID_MODULE_D, true);
        MavenProject prerequisiteE = addModuleMock(AID_MODULE_E, true);
        addModuleMock("module-F", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.buildUpstreamMode, "changed");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");
        addGibProperty(Property.prerequisiteModules, AID_MODULE_A+"="+AID_MODULE_C+" "+AID_MODULE_B+"="+AID_MODULE_D+","+AID_MODULE_E);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMockWithPrerequisites, changedModuleMockNoPrerequisites, prerequisiteD, prerequisiteE));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMockWithPrerequisites);
        assertProjectPropertiesEqual(changedModuleMockNoPrerequisites);
        assertProjectPropertiesEqual(prerequisiteD);
        assertProjectPropertiesEqual(prerequisiteE);
    }


    public void setUpAndDownstreamsForTestOnlyScenarioTests(MavenProject changedModuleMock,
            MavenProject dependentModuleMock, MavenProject transitiveDependentModuleMock) {
        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setUpstreamProjects(transitiveDependentModuleMock, dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);
        setDownstreamProjects(dependentModuleMock, transitiveDependentModuleMock);
        setDownstreamProjectsNonTransitive(dependentModuleMock, transitiveDependentModuleMock);
    }


    public Dependency buildTestJarDependency(MavenProject changedModuleMock) {
        Dependency dep = new Dependency();
        dep.setType("test-jar");
        dep.setArtifactId(changedModuleMock.getArtifactId());
        dep.setGroupId(changedModuleMock.getGroupId());
        dep.setVersion(changedModuleMock.getVersion());
        dep.setScope("test");
        return dep;
    }
}

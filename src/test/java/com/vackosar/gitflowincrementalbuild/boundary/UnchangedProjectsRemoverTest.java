package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 *
 * @author famod
 */
public class UnchangedProjectsRemoverTest extends BaseUnchangedProjectsRemoverTest {

    private static final String AID_MODULE_B_DEP_WAR = AID_MODULE_B + "-dependent-war";

    @Test
    public void nothingChanged() throws GitAPIException, IOException {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goal").isEqualTo(Collections.singletonList("validate"));

        verify(mavenSessionMock).setProjects(Collections.singletonList(moduleA));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(moduleB);
    }

    @Test
    public void nothingChanged_buildAllIfNoChanges() throws GitAPIException, IOException {
        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_B, false);

        addGibProperty(Property.buildAllIfNoChanges, "true");
        addGibProperty(Property.skipTestsForUpstreamModules, "true");

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goals").isEqualTo(Collections.emptyList());

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, "maven.test.skip", "true");
        assertProjectPropertiesEqual(unchangedModuleMock, "maven.test.skip", "true");
    }

    // mvn -f ... for a multi-module-submodule
    @Test
    public void nothingChanged_singleModule_withSubmodules() throws GitAPIException, IOException {
        // note: a more realistic setup would require a proper parent/root
        moduleA.getModel().addModule("test");

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goal").isEqualTo(Collections.singletonList("validate"));

        verify(mavenSessionMock).setProjects(Collections.singletonList(moduleA));
        verify(moduleA, Mockito.times(2)).getModel();   // +1 due to test setup

        assertProjectPropertiesEqual(moduleA);
    }

    // mvn -N ... for a multi-module-submodule
    @Test
    public void nothingChanged_singleModule_withSubmodules_nonRecursive() throws GitAPIException, IOException {
        // note: a more realistic setup would require a proper parent/root
        moduleA.getModel().addModule("test");
        when(mavenExecutionRequestMock.isRecursive()).thenReturn(false);

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goals").isEqualTo(Collections.emptyList());

        verify(mavenSessionMock, never()).setProjects(anyList());
        verify(moduleA).getModel();   // only once due to test setup

        assertProjectPropertiesEqual(moduleA);
    }

    // mvn -f module-B (or unusal case of a non-multi-module project)
    @Test
    public void nothingChanged_singleModule_leaf() throws GitAPIException, IOException {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);

        // emulate -f module-B
        overrideProjects(moduleB);

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goals").isEqualTo(Collections.emptyList());

        verify(mavenSessionMock, never()).setProjects(anyList());
        verify(moduleB).getModel();

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(moduleB);
    }

    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildUpstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.skipTestsForUpstreamModules, "true");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, "maven.test.skip", "true");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules_testJarGoal() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.skipTestsForUpstreamModules, "true");

        addMockedTestJarExecution(moduleA);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, "skipTests", "true");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.argsForUpstreamModules, "enforcer.skip=true argWithNoValue");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, "enforcer.skip", "true", "argWithNoValue", "");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        addGibProperty(Property.buildUpstreamMode, "changed");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedModuleMock);
        assertProjectPropertiesEqual(dependsOnBothModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        addGibProperty(Property.buildUpstreamMode, "changed");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedModuleMock);
        assertProjectPropertiesEqual(dependsOnBothModuleMock);
    }

    // linear: A <- B <- C <- D
    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules_linear() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedIntermediateModuleMock = addModuleMock("module-C", false);
        MavenProject dependsOnIntermediateModuleMock = addModuleMock("module-D", true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpstreamProjects(dependsOnIntermediateModuleMock, unchangedIntermediateModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);
        setDownstreamProjects(unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);

        addGibProperty(Property.buildUpstreamMode, "changed");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedIntermediateModuleMock);
        assertProjectPropertiesEqual(dependsOnIntermediateModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        addGibProperty(Property.buildUpstreamMode, "impacted");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedModuleMock);
        assertProjectPropertiesEqual(dependsOnBothModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        addGibProperty(Property.buildUpstreamMode, "impacted");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedModuleMock, "foo", "bar");
        assertProjectPropertiesEqual(dependsOnBothModuleMock);
    }

    // linear: A <- B <- C <- D
    @Test
    public void singleChanged_buildUpstream_modeImpacted_argsForUpstreamModules_linear() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedIntermediateModuleMock = addModuleMock("module-C", false);
        MavenProject dependsOnIntermediateModuleMock = addModuleMock("module-D", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpstreamProjects(dependsOnIntermediateModuleMock, unchangedIntermediateModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);
        setDownstreamProjects(unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);

        addGibProperty(Property.buildUpstreamMode, "impacted");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock));

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(unchangedIntermediateModuleMock);
        assertProjectPropertiesEqual(dependsOnIntermediateModuleMock);
    }

    @Test
    public void singleChanged_buildAll_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.argsForUpstreamModules, "enforcer.skip=true argWithNoValue");
        addGibProperty(Property.buildAll, "true");

        underTest.act(config());

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, "enforcer.skip", "true", "argWithNoValue", "");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildDownstream_enabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        // buildDownstream is enabled by default!

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        addGibProperty(Property.buildDownstream, "false");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled_buildAll_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        addGibProperty(Property.buildDownstream, "false");
        addGibProperty(Property.buildAll, "true");
        addGibProperty(Property.argsForUpstreamModules, "foo=bar");

        underTest.act(config());

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, "foo", "bar");
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(dependentModuleMock);
    }

    @Test
    public void singleChanged_buildDownstream_testOnly() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_overriddenByChange() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", true);    // changed, sic!
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal_testDep() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);
        Dependency dep = buildTestJarDependency(changedModuleMock);
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(dep));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal_testDep_transitive() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);
        Dependency dep = buildTestJarDependency(changedModuleMock);
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(dep));

        addMockedTestJarExecution(dependentModuleMock);
        Dependency depTransitive = buildTestJarDependency(dependentModuleMock);
        when(transitiveDependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(depTransitive));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal_compileDep_transitive() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForTestOnlyScenarioTests(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);
        Dependency dep = buildTestJarDependency(changedModuleMock);
        dep.setScope("compile");
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(dep));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, /* sic! */ transitiveDependentModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, moduleA.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_conditionalMatches() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, changedModuleMock.getArtifactId() + "=" + moduleA.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_conditionalMatchesNot() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, changedModuleMock.getArtifactId() + "=X");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        addGibProperty(Property.forceBuildModules, moduleA.getArtifactId() + "," + unchangedModuleMock.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_oneWildcard() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_A + "-2", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A + ".*");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*-C");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards_secondMatchesNot() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*-X");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_mixWithConditional() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*B=.*-C");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_twoTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentJar = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setDownstreamProjects(changedProjectMock, dependentWar, dependentJar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentJar));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_twoTransitive_twoExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentEar = addModuleMock(AID_MODULE_B + "-dependent-ear", false, "ear");

        setDownstreamProjects(changedProjectMock, dependentWar, dependentEar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war,ear");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_buildAll() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");
        addGibProperty(Property.buildAll, "true");

        underTest.act(config());

        verify(mavenSessionMock, never())
                .setProjects(anyList());
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");
        addGibProperty(Property.forceBuildModules, dependentWar.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeDownstreamModulesPackagedAs_changedNotExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained!

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeDownstreamModulesPackagedAs_changedNotExcluded_transitive() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained - even if depending on changedProjectMock!
        setDownstreamProjects(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    private void setUpAndDownstreamsForBuildUpstreamModeTests(MavenProject changedModuleMock, MavenProject unchangedModuleMock,
            MavenProject dependsOnBothModuleMock) {
        // dependsOnBothModuleMock directly depends on both changedModuleMock & unchangedModuleMock + transitively on moduleA
        setUpstreamProjects(dependsOnBothModuleMock, changedModuleMock, unchangedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependsOnBothModuleMock);
        setDownstreamProjects(unchangedModuleMock, dependsOnBothModuleMock);
        // downstream of moduleA are handled automatically in addModuleMock()
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

    private static void addMockedTestJarExecution(MavenProject module) {
        Plugin pluginMock = mock(Plugin.class);
        PluginExecution execMock = mock(PluginExecution.class);
        when(execMock.getGoals()).thenReturn(Collections.singletonList("test-jar"));
        when(pluginMock.getExecutions()).thenReturn(Collections.singletonList(execMock));
        when(module.getBuildPlugins()).thenReturn(Collections.singletonList(pluginMock));
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

package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import io.github.gitflowincrementalbuilder.config.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 *
 * @author famod
 */
public class UnchangedProjectsRemoverTest extends BaseUnchangedProjectsRemoverTest {

    private static final String AID_MODULE_B_DEP_WAR = AID_MODULE_B + "-dependent-war";

    @Test
    public void nothingChanged() {
        MavenProject moduleB = addModuleMock(AID_MODULE_B, false);

        underTest.act(config());

        assertThat(mavenSessionMock.getGoals()).as("Unexpected goal").isEqualTo(Collections.singletonList("validate"));

        verify(mavenSessionMock).setProjects(Collections.singletonList(moduleA));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(moduleB);
    }

    @Test
    public void nothingChanged_buildAllIfNoChanges() {
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
    public void nothingChanged_singleModule_withSubmodules() {
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
    public void nothingChanged_singleModule_withSubmodules_nonRecursive() {
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
    public void nothingChanged_singleModule_leaf() {
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
    public void singleChanged() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildUpstream() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.skipTestsForUpstreamModules, "true");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, "maven.test.skip", "true");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules_testJarGoal() {
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
    public void singleChanged_buildUpstream_argsForUpstreamModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        addGibProperty(Property.argsForUpstreamModules, "enforcer.skip=true argWithNoValue");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, "enforcer.skip", "true", "argWithNoValue", "");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged() {
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
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules() {
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
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules_linear() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedIntermediateModuleMock = addModuleMock("module-C", false);
        MavenProject dependsOnIntermediateModuleMock = addModuleMock("module-D", true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpstreamProjects(dependsOnIntermediateModuleMock, unchangedIntermediateModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, unchangedIntermediateModuleMock);
        setDownstreamProjectsNonTransitive(unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);

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
    public void singleChanged_buildUpstream_modeImpacted() {
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
    public void singleChanged_buildUpstream_modeImpacted_argsForUpstreamModules() {
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
    public void singleChanged_buildUpstream_modeImpacted_argsForUpstreamModules_linear() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedIntermediateModuleMock = addModuleMock("module-C", false);
        MavenProject dependsOnIntermediateModuleMock = addModuleMock("module-D", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpstreamProjects(dependsOnIntermediateModuleMock, unchangedIntermediateModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, unchangedIntermediateModuleMock);
        setDownstreamProjectsNonTransitive(unchangedIntermediateModuleMock, dependsOnIntermediateModuleMock);

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
    public void singleChanged_buildAll_argsForUpstreamModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.argsForUpstreamModules, "enforcer.skip=true argWithNoValue");
        addGibProperty(Property.buildAll, "true");

        underTest.act(config());

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, "enforcer.skip", "true", "argWithNoValue", "");
        assertProjectPropertiesEqual(changedModuleMock);
    }

    @Test
    public void singleChanged_buildDownstream_enabled() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);

        // buildDownstream is enabled by default!

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);

        addGibProperty(Property.buildDownstream, "false");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled_buildAll_argsForUpstreamModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);

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
    public void singleChanged_buildDownstream_testOnly() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        // changedModuleMock <- dependentModuleMock (scope compile) is set up already

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_overriddenByChange() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", true);    // changed, sic!
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal_testDep() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(changedModuleMock.getContextValue(ChangedProjects.CTX_TEST_ONLY)).thenReturn(Boolean.TRUE);
        addMockedTestJarExecution(changedModuleMock);
        Dependency dep = buildTestJarDependency(changedModuleMock);
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(dep));

        Dependency depTransitive = buildTestJarDependency(dependentModuleMock);
        when(transitiveDependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(depTransitive));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testOnly_testJarGoal_testDep_transitive() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

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
    public void singleChanged_buildDownstream_testOnly_testJarGoal_compileDep_transitive() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

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
    public void singleChanged_buildDownstream_testDep() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        Dependency depToChangedModuleMock = buildDependency(changedModuleMock, "test");
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(depToChangedModuleMock));
        // depToDependentModuleMock <- transitiveDependentModuleMock (scope compile) is set up already

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_testDep_minimal() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        Dependency depToChangedModuleMock = buildDependency(changedModuleMock, "test");
        depToChangedModuleMock.setType("pom");
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("*");
        exclusion.setArtifactId("*");
        depToChangedModuleMock.setExclusions(Collections.singletonList(exclusion));
        when(dependentModuleMock.getDependencies()).thenReturn(Collections.singletonList(depToChangedModuleMock));
        // depToDependentModuleMock <- transitiveDependentModuleMock (scope compile) is set up already

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_parent() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(dependentModuleMock.getDependencies()).thenReturn(Collections.emptyList());
        when(changedModuleMock.getPackaging()).thenReturn("pom");
        when(dependentModuleMock.getParent()).thenReturn(changedModuleMock);
        // depToDependentModuleMock <- transitiveDependentModuleMock (scope compile) is set up already

        Mockito.clearInvocations(dependentModuleMock);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
        verify(dependentModuleMock, never()).getDependencies();
    }

    @Test
    public void singleChanged_buildDownstream_noDep() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);
        MavenProject transitiveDependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar-transitive", false);

        setUpAndDownstreamsForThreeChainedModules(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock);

        // buildDownstream is enabled by default!

        when(dependentModuleMock.getDependencies()).thenReturn(Collections.emptyList());
        // depToDependentModuleMock <- transitiveDependentModuleMock (scope compile) is set up already

        Mockito.clearInvocations(dependentModuleMock);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock, transitiveDependentModuleMock));
        verify(dependentModuleMock).getDependencies();
    }

    @Test
    public void singleChanged_buildDownstream_argsForDownstreamModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);

        // buildDownstream is enabled by default!
        addGibProperty(Property.argsForDownstreamModules, "foo=bar baz=bing");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
        assertProjectPropertiesEqual(moduleA);
        assertProjectPropertiesEqual(changedModuleMock);
        assertProjectPropertiesEqual(dependentModuleMock, "foo", "bar", "baz", "bing");
    }

    @Test
    public void singleChanged_forceBuildModules() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, moduleA.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_conditionalMatches() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, changedModuleMock.getArtifactId() + "=" + moduleA.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_conditionalMatchesNot() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        addGibProperty(Property.forceBuildModules, changedModuleMock.getArtifactId() + "=X");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        addGibProperty(Property.forceBuildModules, moduleA.getArtifactId() + "," + unchangedModuleMock.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_oneWildcard() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_A + "-2", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A + ".*");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*-C");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards_secondMatchesNot() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*-X");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_mixWithConditional() {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("module-C", false);

        addGibProperty(Property.forceBuildModules, AID_MODULE_A +  ".*,.*B=.*-C");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_oneExcluded() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_twoTransitive_oneExcluded() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentJar = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar, dependentJar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentJar));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_twoTransitive_twoExcluded() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentEar = addModuleMock(AID_MODULE_B + "-dependent-ear", false, "ear");

        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar, dependentEar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war,ear");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_buildAll() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");
        addGibProperty(Property.buildAll, "true");

        underTest.act(config());

        verify(mavenSessionMock, never())
                .setProjects(anyList());
    }

    @Test
    public void singleChanged_excludeDownstreamModulesPackagedAs_oneTransitive_forceBuildModules() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");
        addGibProperty(Property.forceBuildModules, dependentWar.getArtifactId());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeDownstreamModulesPackagedAs_changedNotExcluded() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained!

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeDownstreamModulesPackagedAs_changedNotExcluded_transitive() {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained - even if depending on changedProjectMock!
        setDownstreamProjectsNonTransitive(changedProjectMock, dependentWar);

        addGibProperty(Property.excludeDownstreamModulesPackagedAs, "war");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    private void setUpAndDownstreamsForBuildUpstreamModeTests(MavenProject changedModuleMock, MavenProject unchangedModuleMock,
            MavenProject dependsOnBothModuleMock) {
        // dependsOnBothModuleMock directly depends on both changedModuleMock & unchangedModuleMock + transitively on moduleA
        setUpstreamProjects(dependsOnBothModuleMock, changedModuleMock, unchangedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependsOnBothModuleMock);
        setDownstreamProjectsNonTransitive(unchangedModuleMock, dependsOnBothModuleMock);
        // downstream of moduleA are handled automatically in addModuleMock()
    }

    private void setUpAndDownstreamsForThreeChainedModules(MavenProject changedModuleMock,
            MavenProject dependentModuleMock, MavenProject transitiveDependentModuleMock) {
        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setUpstreamProjects(transitiveDependentModuleMock, dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjectsNonTransitive(changedModuleMock, dependentModuleMock);
        setDownstreamProjectsNonTransitive(dependentModuleMock, transitiveDependentModuleMock);
    }

    private static void addMockedTestJarExecution(MavenProject module) {
        Plugin pluginMock = mock(Plugin.class, withSettings().strictness(Strictness.LENIENT));
        when(pluginMock.getArtifactId()).thenReturn("maven-jar-plugin");
        PluginExecution execMock = mock(PluginExecution.class, withSettings().strictness(Strictness.LENIENT));
        when(execMock.getGoals()).thenReturn(Collections.singletonList("test-jar"));
        when(execMock.getConfiguration()).thenReturn(mock(Xpp3Dom.class));  // omit explicit classifier here
        when(pluginMock.getExecutions()).thenReturn(Collections.singletonList(execMock));
        when(module.getBuildPlugins()).thenReturn(Collections.singletonList(pluginMock));
    }

    private Dependency buildTestJarDependency(MavenProject changedModuleMock) {
        Dependency dep = buildDependency(changedModuleMock, "test");
        dep.setType("test-jar");
        return dep;
    }
}

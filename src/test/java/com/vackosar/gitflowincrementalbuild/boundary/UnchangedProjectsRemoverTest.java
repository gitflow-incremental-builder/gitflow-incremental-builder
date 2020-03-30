package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Test;
import com.google.common.collect.ImmutableMap;
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
        addModuleMock(AID_MODULE_B, false);

        underTest.act();

        assertEquals("Unexpected goal", Collections.singletonList("validate"), mavenSessionMock.getGoals());

        verify(mavenSessionMock).setProjects(Collections.singletonList(moduleA));
    }

    @Test
    public void nothingChanged_buildAllIfNoChanges() throws GitAPIException, IOException {
        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_B, false);

        projectProperties.put(Property.buildAllIfNoChanges.fullName(), "true");
        projectProperties.put(Property.skipTestsForUpstreamModules.fullName(), "true");

        underTest.act();

        assertEquals("Unexpected goals", Collections.emptyList(), mavenSessionMock.getGoals());

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("maven.test.skip", "true"));
        assertProjectPropertiesEqual(unchangedModuleMock, ImmutableMap.of("maven.test.skip", "true"));
    }

    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        underTest.act();

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildUpstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.skipTestsForUpstreamModules.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("maven.test.skip", "true"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForUpstreamModules_jarGoal() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.skipTestsForUpstreamModules.fullName(), "true");

        Plugin pluginMock = mock(Plugin.class);
        PluginExecution execMock = mock(PluginExecution.class);
        when(execMock.getGoals()).thenReturn(Collections.singletonList("test-jar"));
        when(pluginMock.getExecutions()).thenReturn(Collections.singletonList(execMock));
        when(moduleA.getBuildPlugins()).thenReturn(Collections.singletonList(pluginMock));

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("skipTests", "true"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.argsForUpstreamModules.fullName(), "enforcer.skip=true argWithNoValue");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("enforcer.skip", "true", "argWithNoValue", ""));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "changed");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "changed");
        projectProperties.put(Property.argsForUpstreamModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "impacted");   // this is also the default value!

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "impacted");   // is also the default value!
        projectProperties.put(Property.argsForUpstreamModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock));

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildAll_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        projectProperties.put(Property.argsForUpstreamModules.fullName(), "enforcer.skip=true argWithNoValue");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("enforcer.skip", "true", "argWithNoValue", ""));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildDownstream_enabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        // buildDownstream is enabled by default!

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        projectProperties.put(Property.buildDownstream.fullName(), "false");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled_buildAll_argsForUpstreamModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentModuleMock = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, moduleA);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);

        projectProperties.put(Property.buildDownstream.fullName(), "false");
        projectProperties.put(Property.buildAll.fullName(), "true");
        projectProperties.put(Property.argsForUpstreamModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock, never()).setProjects(anyList());

        assertProjectPropertiesEqual(moduleA, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependentModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);

        projectProperties.put(Property.forceBuildModules.fullName(), moduleA.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(moduleA, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(),
                moduleA.getArtifactId() + "," + unchangedModuleMock.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_oneWildcard() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_A + "-2", false);

        projectProperties.put(Property.forceBuildModules.fullName(), AID_MODULE_A + ".*");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(AID_MODULE_B, true);
        MavenProject unchangedModuleMock = addModuleMock("module-C", false);

        projectProperties.put(Property.forceBuildModules.fullName(), AID_MODULE_A +  ".*,.*-C");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(moduleA, changedModuleMock, unchangedModuleMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentJar = addModuleMock(AID_MODULE_B + "-dependent-jar", false);

        setDownstreamProjects(changedProjectMock, dependentWar, dependentJar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentJar));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_twoExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");
        MavenProject dependentEar = addModuleMock(AID_MODULE_B + "-dependent-ear", false, "ear");

        setDownstreamProjects(changedProjectMock, dependentWar, dependentEar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war,ear");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_buildAll() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock, never())
                .setProjects(anyList());
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.forceBuildModules.fullName(), dependentWar.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained!

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded_transitive() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(AID_MODULE_B, true);
        MavenProject dependentWar = addModuleMock(AID_MODULE_B_DEP_WAR, true, "war");

        // war module is changed, must be retained - even if depending on changedProjectMock!
        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

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
}

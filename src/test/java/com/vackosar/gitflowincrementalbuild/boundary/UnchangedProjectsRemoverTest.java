/**
 * 
 */
package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.google.common.collect.ImmutableMap;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 * 
 * @author famod
 */
@RunWith(MockitoJUnitRunner.class)
public class UnchangedProjectsRemoverTest {

    private static final String ARTIFACT_ID_1 = "first-unchanged-module";
    private static final String ARTIFACT_ID_2 = "changed-module";
    private static final String ARTIFACT_ID_2_DEP_WAR = ARTIFACT_ID_2 + "-dependent-war";

    @Mock(name = ARTIFACT_ID_1)
    private MavenProject mavenProjectMock;

    @Mock
    private MavenExecutionRequest mavenExecutionRequestMock;

    @Mock
    private MavenSession mavenSessionMock;

    @Mock
    private ProjectDependencyGraph projectDependencyGraphMock;

    @Mock
    private ChangedProjects changedProjectsMock;

    @InjectMocks
    private UnchangedProjectsRemover underTest;

    private final List<MavenProject> projects = new ArrayList<>(); 
    private final Set<MavenProject> changedProjects = new LinkedHashSet<>();
    private final Properties projectProperties = new Properties();

    @Before
    public void setup() throws GitAPIException, IOException {
        when(mavenProjectMock.getProperties()).thenReturn(projectProperties);
        when(mavenProjectMock.getBasedir()).thenReturn(new File("."));
        when(mavenProjectMock.getArtifactId()).thenReturn(ARTIFACT_ID_1);

        when(mavenSessionMock.getCurrentProject()).thenReturn(mavenProjectMock);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mavenProjectMock);

        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        projects.add(mavenProjectMock);
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(projectDependencyGraphMock);
        when(changedProjectsMock.get()).thenReturn(changedProjects);

        when(mavenSessionMock.getGoals()).thenReturn(new ArrayList<>());

        Whitebox.setInternalState(underTest, new Configuration.Provider(mavenSessionMock));
    }

    @Test
    public void nothingChanged() throws GitAPIException, IOException {
        addModuleMock(ARTIFACT_ID_2, false);

        underTest.act();

        assertEquals("Unexpected goal", Collections.singletonList("validate"), mavenSessionMock.getGoals());

        verify(mavenSessionMock).setProjects(Collections.singletonList(mavenProjectMock));
    }

    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        underTest.act();

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildUpstream() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.skipTestsForNotImpactedModules.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("maven.test.skip", "true"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_skipTestsForNotImpactedModules_jarGoal() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.skipTestsForNotImpactedModules.fullName(), "true");

        Plugin pluginMock = mock(Plugin.class);
        PluginExecution execMock = mock(PluginExecution.class);
        when(execMock.getGoals()).thenReturn(Collections.singletonList("test-jar"));
        when(pluginMock.getExecutions()).thenReturn(Collections.singletonList(execMock));
        when(mavenProjectMock.getBuildPlugins()).thenReturn(Collections.singletonList(pluginMock));

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("skipTests", "true"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "enforcer.skip=true argWithNoValue");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("enforcer.skip", "true", "argWithNoValue", ""));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "changed");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependsOnBothModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeChanged_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "changed");
        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependsOnBothModuleMock, mavenProjectMock));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "impacted");   // this is also the default value!

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependsOnBothModuleMock, mavenProjectMock, unchangedModuleMock));

        assertProjectPropertiesEqual(mavenProjectMock, Collections.emptyMap());
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildUpstream_modeImpacted_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);
        MavenProject dependsOnBothModuleMock = addModuleMock("changed-and-unchanged-dependent", false);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        setUpAndDownstreamsForBuildUpstreamModeTests(changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);

        projectProperties.put(Property.buildUpstreamMode.fullName(), "impacted");   // is also the default value!
        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependsOnBothModuleMock, mavenProjectMock, unchangedModuleMock));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(unchangedModuleMock, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(dependsOnBothModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildAll_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "enforcer.skip=true argWithNoValue");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock, never()).setProjects(anyListOf(MavenProject.class));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("enforcer.skip", "true", "argWithNoValue", ""));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_buildDownstream_enabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentModuleMock = addModuleMock(ARTIFACT_ID_2 + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, mavenProjectMock);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);
        setDownstreamProjects(mavenProjectMock, changedModuleMock, dependentModuleMock);   // just for consistency

        // buildDownstream is enabled by default!

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, dependentModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentModuleMock = addModuleMock(ARTIFACT_ID_2 + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, mavenProjectMock);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);
        setDownstreamProjects(mavenProjectMock, changedModuleMock, dependentModuleMock);   // just for consistency

        projectProperties.put(Property.buildDownstream.fullName(), "false");

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock));
    }

    @Test
    public void singleChanged_buildDownstream_disabled_buildAll_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentModuleMock = addModuleMock(ARTIFACT_ID_2 + "-dependent-jar", false);

        setUpstreamProjects(dependentModuleMock, changedModuleMock, mavenProjectMock);
        setDownstreamProjects(changedModuleMock, dependentModuleMock);
        setDownstreamProjects(mavenProjectMock, changedModuleMock, dependentModuleMock);   // just for consistency

        projectProperties.put(Property.buildDownstream.fullName(), "false");
        projectProperties.put(Property.buildAll.fullName(), "true");
        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "foo=bar");

        underTest.act();

        verify(mavenSessionMock, never()).setProjects(anyListOf(MavenProject.class));

        assertProjectPropertiesEqual(mavenProjectMock, ImmutableMap.of("foo", "bar"));
        assertProjectPropertiesEqual(changedModuleMock, Collections.emptyMap());
        assertProjectPropertiesEqual(dependentModuleMock, Collections.emptyMap());
    }

    @Test
    public void singleChanged_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        projectProperties.put(Property.forceBuildModules.fullName(), mavenProjectMock.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(Arrays.asList(mavenProjectMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(),
                mavenProjectMock.getArtifactId() + "," + unchangedModuleMock.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_oneWildcard() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(), ".*unchanged-module");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(), "first-.*-module,unchanged-.*");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");
        MavenProject dependentJar = addModuleMock(ARTIFACT_ID_2 + "-dependent-jar", false);

        setDownstreamProjects(changedProjectMock, dependentWar, dependentJar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentJar));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_twoExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");
        MavenProject dependentEar = addModuleMock(ARTIFACT_ID_2 + "-dependent-ear", false, "ear");

        setDownstreamProjects(changedProjectMock, dependentWar, dependentEar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war,ear");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_buildAll() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        verify(mavenSessionMock, never())
                .setProjects(anyListOf(MavenProject.class));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.forceBuildModules.fullName(), dependentWar.getArtifactId());

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(dependentWar, changedProjectMock));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, true, "war");

        // war module is changed, must be retained!

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded_transitive() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, true, "war");

        // war module is changed, must be retained - even if depending on changedProjectMock!
        setDownstreamProjects(changedProjectMock, dependentWar);

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    private MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged) {
        return addModuleMock(moduleArtifactId, addToChanged, "jar");
    }

    private MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged, final String packaging) {
        MavenProject changedModuleMock = mock(MavenProject.class, moduleArtifactId);
        when(changedModuleMock.getArtifactId()).thenReturn(moduleArtifactId);
        when(changedModuleMock.getPackaging()).thenReturn(packaging);
        if (addToChanged) {
            changedProjects.add(changedModuleMock);
        }
        projects.add(changedModuleMock);

        when(changedModuleMock.getProperties()).thenReturn(new Properties());

        setUpstreamProjects(changedModuleMock, mavenProjectMock);
        return changedModuleMock;
    }

    private void setUpstreamProjects(MavenProject module, MavenProject... upstreamModules) {
        when(projectDependencyGraphMock.getUpstreamProjects(module, true)).thenReturn(Arrays.asList(upstreamModules));
    }

    private void setDownstreamProjects(MavenProject module, MavenProject... downstreamModules) {
        when(projectDependencyGraphMock.getDownstreamProjects(module, true)).thenReturn(Arrays.asList(downstreamModules));
    }

    private void assertProjectPropertiesEqual(MavenProject project, Map<String, String> expected) {
        TreeMap<String, String> actual = project.getProperties().entrySet().stream()
                .filter(e -> !e.getKey().toString().startsWith(Property.PREFIX))    // we don't want to check for GIB properties here!
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString(),
                        (a, b) -> a,
                        TreeMap::new));
        assertEquals("Unexpected project properties of " + project, new TreeMap<>(expected), actual);
    }

    private void setUpAndDownstreamsForBuildUpstreamModeTests(MavenProject changedModuleMock, MavenProject unchangedModuleMock,
        MavenProject dependsOnBothModuleMock) {
        // dependsOnBothModuleMock directly depends on both changedModuleMock & unchangedModuleMock + transitively on mavenProjectMock
        setUpstreamProjects(dependsOnBothModuleMock, changedModuleMock, unchangedModuleMock, mavenProjectMock);
        setDownstreamProjects(changedModuleMock, dependsOnBothModuleMock);
        setDownstreamProjects(unchangedModuleMock, dependsOnBothModuleMock);
        setDownstreamProjects(mavenProjectMock, changedModuleMock, unchangedModuleMock, dependsOnBothModuleMock);   // just for consistency
    }
}

/**
 * 
 */
package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

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
    private Logger loggerMock;

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

        Whitebox.setInternalState(underTest, new Configuration.Provider(mavenSessionMock));
    }
    
    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_alsoMake_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "enforcer.skip=true argWithNoValue");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(Arrays.asList(changedModuleMock, mavenProjectMock));
        
        assertEquals("Unexpected value for enforcer.skip",
                "true", mavenProjectMock.getProperties().getProperty("enforcer.skip"));
        assertEquals("Unexpected value for argWithNoValue",
                "", mavenProjectMock.getProperties().getProperty("argWithNoValue"));
        assertEquals("Unexpected properties for unchanged project",
                new Properties(), changedModuleMock.getProperties());
    }

    @Test
    public void singleChanged_buildAll_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        projectProperties.put(Property.argsForNotImpactedModules.fullName(), "enforcer.skip=true argWithNoValue");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        Mockito.verify(mavenSessionMock, never()).setProjects(anyListOf(MavenProject.class));
        
        assertEquals("Unexpected value for enforcer.skip",
                "true", mavenProjectMock.getProperties().getProperty("enforcer.skip"));
        assertEquals("Unexpected value for argWithNoValue",
                "", mavenProjectMock.getProperties().getProperty("argWithNoValue"));
        assertEquals("Unexpected properties for unchanged project",
                new Properties(), changedModuleMock.getProperties());
    }

    @Test
    public void singleChanged_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);

        projectProperties.put(Property.forceBuildModules.fullName(), mavenProjectMock.getArtifactId());

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(Arrays.asList(mavenProjectMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(),
                mavenProjectMock.getArtifactId() + "," + unchangedModuleMock.getArtifactId());

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_oneWildcard() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(), ".*unchanged-module");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_twoWildcards() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject unchangedModuleMock = addModuleMock("unchanged-module", false);

        projectProperties.put(Property.forceBuildModules.fullName(), "first-.*-module,unchanged-.*");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedModuleMock, changedModuleMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_oneExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");
        MavenProject dependentJar = addModuleMock(ARTIFACT_ID_2 + "-dependent-jar", false);

        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar, dependentJar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentJar));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_twoTransitive_twoExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");
        MavenProject dependentEar = addModuleMock(ARTIFACT_ID_2 + "-dependent-ear", false, "ear");

        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar, dependentEar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war,ear");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_buildAll() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.buildAll.fullName(), "true");

        underTest.act();

        Mockito.verify(mavenSessionMock, never())
                .setProjects(anyListOf(MavenProject.class));
    }

    @Test
    public void singleChanged_excludeTransitiveModulesPackagedAs_oneTransitive_forceBuildModules() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, false, "war");

        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");
        projectProperties.put(Property.forceBuildModules.fullName(), dependentWar.getArtifactId());

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(dependentWar, changedProjectMock));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, true, "war");

        // war module is changed, must be retained!

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(changedProjectMock, dependentWar));
    }

    @Test
    public void twoChanged_excludeTransitiveModulesPackagedAs_changedNotExcluded_transitive() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addModuleMock(ARTIFACT_ID_2, true);
        MavenProject dependentWar = addModuleMock(ARTIFACT_ID_2_DEP_WAR, true, "war");

        // war module is changed, must be retained - even if depending on changedProjectMock!
        when(projectDependencyGraphMock.getDownstreamProjects(changedProjectMock, true))
                .thenReturn(Arrays.asList(dependentWar));

        projectProperties.put(Property.excludeTransitiveModulesPackagedAs.fullName(), "war");

        underTest.act();

        Mockito.verify(mavenSessionMock).setProjects(
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

        when(projectDependencyGraphMock.getUpstreamProjects(changedModuleMock, true))
                .thenReturn(Collections.singletonList(mavenProjectMock));
        return changedModuleMock;
    }
}

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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

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

    private static final String ARTIFACT_ID_1 = "first-module";
    private static final String ARTIFACT_ID_2 = "changed-module";

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

    private final List<MavenProject> projects = new ArrayList<>(); 
    private final Set<MavenProject> changedProjects = new LinkedHashSet<>();
    
    @Before
    public void setup() throws GitAPIException, IOException {
        addPropertiesToMock(mavenProjectMock);
        when(mavenProjectMock.getBasedir()).thenReturn(new File("."));
        when(mavenProjectMock.getArtifactId()).thenReturn(ARTIFACT_ID_1);
        when(mavenSessionMock.getCurrentProject()).thenReturn(mavenProjectMock);
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        projects.add(mavenProjectMock);
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(projectDependencyGraphMock);
        when(changedProjectsMock.get()).thenReturn(changedProjects);
    }
    
    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addChangedModuleMock(ARTIFACT_ID_2);

        buildUnderTest().act();

        Mockito.verify(mavenSessionMock).setProjects(Collections.singletonList(changedModuleMock));
    }

    @Test
    public void singleChanged_alsoMake_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addChangedModuleMock(ARTIFACT_ID_2);

        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        ImmutableMap<Property, String> propertyMap = ImmutableMap.of(
                Property.argsForNotImpactedModules, "enforcer.skip=true argWithNoValue");

        buildUnderTest(propertyMap).act();

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
        MavenProject changedModuleMock = addChangedModuleMock(ARTIFACT_ID_2);

        ImmutableMap<Property, String> propertyMap = ImmutableMap.of(
                Property.argsForNotImpactedModules, "enforcer.skip=true argWithNoValue",
                Property.buildAll, "true");

        buildUnderTest(propertyMap).act();

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
        MavenProject changedModuleMock = addChangedModuleMock(ARTIFACT_ID_2);

        ImmutableMap<Property, String> propertyMap = ImmutableMap.of(
                Property.forceBuildModules, mavenProjectMock.getArtifactId());

        buildUnderTest(propertyMap).act();

        Mockito.verify(mavenSessionMock).setProjects(Arrays.asList(mavenProjectMock, changedModuleMock));
    }

    @Test
    public void singleChanged_forceBuildModules_two() throws GitAPIException, IOException {
        MavenProject changedProjectMock = addChangedModuleMock(ARTIFACT_ID_2);
        MavenProject unchangedProjectMock = addChangedModuleMock("unchanged-module");
        changedProjects.remove(unchangedProjectMock);

        ImmutableMap<Property, String> propertyMap = ImmutableMap.of(
                Property.forceBuildModules,
                mavenProjectMock.getArtifactId() + "," + unchangedProjectMock.getArtifactId());

        buildUnderTest(propertyMap).act();

        Mockito.verify(mavenSessionMock).setProjects(
                Arrays.asList(mavenProjectMock, unchangedProjectMock, changedProjectMock));
    }

    private Properties addPropertiesToMock(MavenProject mavenProjectMock) {
        Properties properties = new Properties();
        when(mavenProjectMock.getProperties()).thenReturn(properties);
        return properties;
    }

    private MavenProject addChangedModuleMock(String moduleArtifactId) {
        MavenProject changedModuleMock = mock(MavenProject.class, moduleArtifactId);
        when(changedModuleMock.getArtifactId()).thenReturn(moduleArtifactId);
        changedProjects.add(changedModuleMock);
        projects.add(changedModuleMock);
        addPropertiesToMock(changedModuleMock);

        when(projectDependencyGraphMock.getUpstreamProjects(changedModuleMock, true))
                .thenReturn(Collections.singletonList(mavenProjectMock));
        return changedModuleMock;
    }

    private UnchangedProjectsRemover buildUnderTest() throws IOException {
        return buildUnderTest(ImmutableMap.of());
    }

    private UnchangedProjectsRemover buildUnderTest(ImmutableMap<Property, String> propertyMap) throws IOException {
        Configuration configuration;
        Properties origProps = (Properties) System.getProperties().clone();
        try {
            propertyMap.forEach((k, v) -> System.setProperty(k.fullName(), v));
            configuration = new Configuration(mavenSessionMock);
        } finally {
            System.setProperties(origProps);
        }
        return new UnchangedProjectsRemover(configuration, loggerMock, changedProjectsMock, mavenSessionMock);
    }
}

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
import java.util.List;
import java.util.Properties;

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

    @Mock
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
    
    @Before
    public void setup() {
        addPropertiesToMock(mavenProjectMock);
        when(mavenProjectMock.getBasedir()).thenReturn(new File("."));
        when(mavenSessionMock.getCurrentProject()).thenReturn(mavenProjectMock);
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        projects.add(mavenProjectMock);
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(projectDependencyGraphMock);
    }
    
    @Test
    public void singleChanged() throws GitAPIException, IOException {
        MavenProject changedProjectMock = Mockito.mock(MavenProject.class);
        when(changedProjectsMock.get()).thenReturn(Collections.singleton(changedProjectMock));
        projects.add(changedProjectMock);

        buildUnderTest().act();

        Mockito.verify(mavenSessionMock).setProjects(Collections.singletonList(changedProjectMock));
    }

    @Test
    public void singleChanged_alsoMake_argsForNotImpactedModules() throws GitAPIException, IOException {
        MavenProject changedModuleMock = addChangedModuleMock();

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
        MavenProject changedModuleMock = addChangedModuleMock();

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

    private Properties addPropertiesToMock(MavenProject mavenProjectMock) {
        Properties properties = new Properties();
        when(mavenProjectMock.getProperties()).thenReturn(properties);
        return properties;
    }

    private MavenProject addChangedModuleMock() throws GitAPIException, IOException {
        MavenProject changedModuleMock = mock(MavenProject.class);
        when(changedProjectsMock.get()).thenReturn(Collections.singleton(changedModuleMock));
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

package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 * 
 * @author famod
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class BaseUnchangedProjectsRemoverTest {

    protected static final String AID_MODULE_A = "module-A";
    protected static final String AID_MODULE_B = "module-B";
    protected static final String AID_MODULE_C = "module-C";

    /**
     * The first module in the chain, unchanged by default.
     */
    @Mock(name = AID_MODULE_A)
    protected MavenProject moduleA;

    @Mock(lenient = true)
    protected MavenExecutionRequest mavenExecutionRequestMock;

    @Mock(lenient = true)
    protected MavenSession mavenSessionMock;

    @Mock
    protected ProjectDependencyGraph projectDependencyGraphMock;

    @Mock
    protected ChangedProjects changedProjectsMock;

    @InjectMocks
    protected UnchangedProjectsRemover underTest;

    protected final List<MavenProject> projects = new ArrayList<>(); 
    protected final Set<MavenProject> changedProjects = new LinkedHashSet<>();
    protected final Properties projectProperties = new Properties();

    @Before
    public void before() throws GitAPIException, IOException {
        when(moduleA.getProperties()).thenReturn(projectProperties);
        when(moduleA.getArtifactId()).thenReturn(AID_MODULE_A);

        when(mavenSessionMock.getCurrentProject()).thenReturn(moduleA);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(moduleA);

        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        projects.add(moduleA);
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(projectDependencyGraphMock);
        when(changedProjectsMock.get()).thenReturn(changedProjects);

        when(mavenSessionMock.getGoals()).thenReturn(new ArrayList<>());

        Whitebox.setInternalState(underTest, new Configuration.Provider(mavenSessionMock));
    }

    protected MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged) {
        return addModuleMock(moduleArtifactId, addToChanged, "jar");
    }

    protected MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged, final String packaging) {
        MavenProject newModuleMock = mock(MavenProject.class, withSettings().name(moduleArtifactId).lenient());
        when(newModuleMock.getArtifactId()).thenReturn(moduleArtifactId);
        when(newModuleMock.getPackaging()).thenReturn(packaging);
        if (addToChanged) {
            changedProjects.add(newModuleMock);
        }
        projects.add(newModuleMock);

        when(newModuleMock.getProperties()).thenReturn(new Properties());

        setUpstreamProjects(newModuleMock, moduleA);
        // update downstream of module-A
        Set<MavenProject> downstreamOfModuleA = new LinkedHashSet<>(projectDependencyGraphMock.getDownstreamProjects(moduleA, true));
        downstreamOfModuleA.add(newModuleMock);
        setDownstreamProjects(moduleA, downstreamOfModuleA.toArray(new MavenProject[0]));

        return newModuleMock;
    }

    protected void setUpstreamProjects(MavenProject module, MavenProject... upstreamModules) {
        when(projectDependencyGraphMock.getUpstreamProjects(module, true)).thenReturn(Arrays.asList(upstreamModules));
    }

    protected void setDownstreamProjects(MavenProject module, MavenProject... downstreamModules) {
        when(projectDependencyGraphMock.getDownstreamProjects(module, true)).thenReturn(Arrays.asList(downstreamModules));
    }

    protected void assertProjectPropertiesEqual(MavenProject project, Map<String, String> expected) {
        TreeMap<String, String> actual = project.getProperties().entrySet().stream()
                .filter(e -> !e.getKey().toString().startsWith(Property.PREFIX))    // we don't want to check for GIB properties here!
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString(),
                        (a, b) -> a,
                        TreeMap::new));
        assertEquals("Unexpected project properties of " + project, new TreeMap<>(expected), actual);
    }
}

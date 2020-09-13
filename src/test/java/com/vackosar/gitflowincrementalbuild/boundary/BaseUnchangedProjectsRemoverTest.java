package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

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
import java.util.stream.IntStream;

import org.apache.commons.lang3.Validate;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks.
 *
 * @author famod
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnchangedProjectsRemoverTest {

    protected static final String AID_MODULE_A = "module-A";
    protected static final String AID_MODULE_B = "module-B";
    protected static final String AID_MODULE_C = "module-C";
    protected static final String AID_MODULE_D = "module-D";
    protected static final String AID_MODULE_E = "module-E";

    /**
     * The first module in the chain, unchanged by default.
     */
    protected MavenProject moduleA;

    @Mock(lenient = true)
    protected MavenExecutionRequest mavenExecutionRequestMock;

    @Mock(lenient = true)
    protected MavenSession mavenSessionMock;

    @Mock(lenient = true)
    protected ProjectDependencyGraph projectDependencyGraphMock;

    @Mock(lenient = true)
    protected ChangedProjects changedProjectsMock;

    @InjectMocks
    protected UnchangedProjectsRemover underTest;

    protected final Set<MavenProject> changedProjects = new LinkedHashSet<>();

    /**
     * Value for {@code mavenSessionMock.getProjects()}.
     */
    private final List<MavenProject> projects = new ArrayList<>();

    // gibProperties to be applied to _every_ moduleMock
    // note: at regular runtime (via Maven), each module will automatically contain the properties of its parent(s),
    //       which has to be emulated here, even though this test does not (yet) have an explicit root project
    //       see also: overrideProjects()
    private final Properties gibProperties = new Properties();
    private final List<MavenProject> allModuleMocks = new ArrayList<>();

    @BeforeEach
    void before() throws GitAPIException, IOException {
        moduleA = addModuleMock(AID_MODULE_A, false);

        when(mavenSessionMock.getCurrentProject()).thenReturn(moduleA);

        when(mavenExecutionRequestMock.isRecursive()).thenReturn(true);
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);
        when(mavenSessionMock.getProjects()).thenReturn(projects);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(projectDependencyGraphMock);
        when(changedProjectsMock.get(any(Configuration.class))).thenReturn(changedProjects);

        when(mavenSessionMock.getGoals()).thenReturn(new ArrayList<>());
    }

    protected MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged) {
        return addModuleMock(moduleArtifactId, addToChanged, "jar");
    }

    protected MavenProject addModuleMock(String moduleArtifactId, boolean addToChanged, final String packaging) {
        MavenProject newModuleMock = mock(MavenProject.class, withSettings().name(moduleArtifactId).lenient());
        allModuleMocks.add(newModuleMock);
        when(newModuleMock.getGroupId()).thenReturn("com.vackosar.gitflowincrementalbuild");
        when(newModuleMock.getArtifactId()).thenReturn(moduleArtifactId);
        when(newModuleMock.getVersion()).thenReturn("0.0.1");
        when(newModuleMock.getBasedir()).thenReturn(new File(moduleArtifactId));
        when(newModuleMock.getPackaging()).thenReturn(packaging);
        if (addToChanged) {
            changedProjects.add(newModuleMock);
        }
        projects.add(newModuleMock);    // add to projects that are seen by the session, which can be changed afterwards via overrideProjects()

        when(newModuleMock.getProperties()).thenReturn(new Properties());
        newModuleMock.getProperties().putAll(gibProperties);

        when(newModuleMock.getModel()).thenReturn(new Model());

        if (moduleA != null) {  // support the creation of module-A itself via this method
            setUpstreamProjects(newModuleMock, moduleA);
            // update downstream of module-A
            Set<MavenProject> downstreamOfModuleA = new LinkedHashSet<>(projectDependencyGraphMock.getDownstreamProjects(moduleA, true));
            downstreamOfModuleA.add(newModuleMock);
            setDownstreamProjects(moduleA, downstreamOfModuleA.toArray(new MavenProject[0]));
        }

        return newModuleMock;
    }

    protected void setUpstreamProjects(MavenProject module, MavenProject... upstreamModules) {
        when(projectDependencyGraphMock.getUpstreamProjects(module, true)).thenReturn(Arrays.asList(upstreamModules));
    }

    protected void setDownstreamProjects(MavenProject module, MavenProject... downstreamModules) {
        when(projectDependencyGraphMock.getDownstreamProjects(module, true)).thenReturn(Arrays.asList(downstreamModules));
    }

    protected void addGibProperty(Property property, String value) {
        gibProperties.put(property.prefixedName(), value);
        allModuleMocks.forEach(mod -> mod.getProperties().put(property.prefixedName(), value));
    }

    /**
     * Overrides {@link MavenSession#getProjects()} with the given mocks which is only necessary if <i>not</i> all module mocks
     * that have been created by {@link #addModuleMock(String, boolean, String)} shall end up in the session (e.g. for {@code -pl} cases etc.).
     */
    protected void overrideProjects(MavenProject... moduleMocks) {
        projects.clear();
        projects.addAll(Arrays.asList(moduleMocks));

        // Maven shifts currentProject to the first(!) project (as per Maven 3.6.3 with -pl and -f)
        MavenProject firstProject = moduleMocks[0];
        when(mavenSessionMock.getCurrentProject()).thenReturn(firstProject);
    }

    protected void assertProjectPropertiesEqual(MavenProject project, String... expectedFlat) {
        Validate.validState(expectedFlat.length % 2 == 0, "Odd number of expected properties (need to form pairs).");
        TreeMap<String, String> actual = project.getProperties().entrySet().stream()
                .filter(e -> !e.getKey().toString().startsWith(Property.PREFIX))    // we don't want to check for GIB properties here!
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString(),
                        (a, b) -> a,
                        TreeMap::new));
        Map<String, String> expected = expectedFlat.length == 0
                ? Collections.emptyMap()
                : IntStream.range(0, expectedFlat.length / 2)
                        .map(i -> i * 2)
                        .boxed()
                        .collect(Collectors.toMap(i -> expectedFlat[i], i -> expectedFlat[i + 1], (a, b) -> a, TreeMap::new));
        assertEquals(expected, actual, "Unexpected project properties of " + project);
    }

    protected Configuration config() {
        return new Configuration(mavenSessionMock);
    }
}

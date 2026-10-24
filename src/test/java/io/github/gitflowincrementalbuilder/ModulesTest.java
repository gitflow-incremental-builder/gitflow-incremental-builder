package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModulesTest {

    @Mock
    private MavenSession sessionMock;

    private final Modules underTest = new Modules();

    @Test
    void none() {
        assertThat(underTest.createPathMap(sessionMock)).isEmpty();
    }

    @Test
    void single() {
        MavenProject projMock = mockProject("proj");
        when(sessionMock.getAllProjects()).thenReturn(Arrays.asList(projMock));

        assertThat(underTest.createPathMap(sessionMock)).containsOnly(
                entry(Path.of("proj").toAbsolutePath(), Arrays.asList(projMock)));
    }

    @Test
    void multiple() {
        MavenProject projMock1 = mockProject("proj1");
        MavenProject projMock2 = mockProject("proj2");
        when(sessionMock.getAllProjects()).thenReturn(Arrays.asList(projMock1, projMock2));

        assertThat(underTest.createPathMap(sessionMock)).containsOnly(
                entry(Path.of("proj1").toAbsolutePath(), Arrays.asList(projMock1)),
                entry(Path.of("proj2").toAbsolutePath(), Arrays.asList(projMock2)));
    }

    @Test
    void multiple_twoWithSameDir() {
        MavenProject projMock1 = mockProject("proj1");
        MavenProject projMock2 = mockProject("proj2+3");
        MavenProject projMock3 = mockProject("proj2+3");
        when(sessionMock.getAllProjects()).thenReturn(Arrays.asList(projMock1, projMock2, projMock3));

        assertThat(underTest.createPathMap(sessionMock)).containsOnly(
                entry(Path.of("proj1").toAbsolutePath(), Arrays.asList(projMock1)),
                entry(Path.of("proj2+3").toAbsolutePath(), Arrays.asList(projMock2, projMock3)));
    }

    @Test
    void collectModulesOf_none() {
        MavenProject parent = mockModule("parent");

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent))).isEmpty();
    }

    @Test
    void collectModulesOf_directAndRecursive() {
        MavenProject parent = mockModule("parent", "child1", "child2");
        MavenProject child1 = mockModule("parent/child1", "sub");
        MavenProject child2 = mockModule("parent/child2");
        MavenProject sub = mockModule("parent/child1/sub");
        MavenProject unrelated = mockModule("unrelated");

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent, child1, child2, sub, unrelated)))
                .containsExactlyInAnyOrder(child1, child2, sub);
    }

    @Test
    void collectModulesOf_outOfTree() {
        MavenProject parent = mockModule("parent", "../other");
        MavenProject other = mockModule("other", "nested");
        MavenProject nested = mockModule("other/nested");

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent, other, nested)))
                .containsExactlyInAnyOrder(other, nested);
    }

    @Test
    void collectModulesOf_dirBelowParentButNotDeclared() {
        MavenProject parent = mockModule("parent", "child1");
        MavenProject child1 = mockModule("parent/child1");
        MavenProject notDeclared = mockModule("parent/child2");

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent, child1, notDeclared)))
                .containsExactly(child1);
    }

    @Test
    void collectModulesOf_pomFileReference() {
        MavenProject parent = mockModule("parent", "child1/special-pom.xml");
        MavenProject child1 = mockModule("parent/child1");
        when(child1.getFile()).thenReturn(new File("parent/child1/special-pom.xml"));

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent, child1))).containsExactly(child1);
    }

    @Test
    void collectModulesOf_candidatesOnly() {
        MavenProject parent = mockModule("parent", "child1", "child2");
        MavenProject child1 = mockModule("parent/child1");

        assertThat(Modules.collectModulesOf(parent, Arrays.asList(parent, child1))).containsExactly(child1);
    }

    @Test
    void collectModulesOf_cycle() {
        MavenProject a = mockModule("a", "../b");
        MavenProject b = mockModule("b", "../a");

        assertThat(Modules.collectModulesOf(a, Arrays.asList(a, b))).containsExactlyInAnyOrder(a, b);
    }

    public MavenProject mockProject(String dirName) {
        MavenProject projMock = Mockito.mock(MavenProject.class);
        when(projMock.getBasedir()).thenReturn(new File(dirName));
        return projMock;
    }

    private MavenProject mockModule(String dirName, String... modules) {
        MavenProject projMock = Mockito.mock(MavenProject.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(projMock.getBasedir()).thenReturn(new File(dirName));
        Model model = new Model();
        Arrays.stream(modules).forEach(model::addModule);
        when(projMock.getModel()).thenReturn(model);
        return projMock;
    }
}
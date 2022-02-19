package io.github.gitflowincrementalbuilder.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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
                entry(Paths.get("proj").toAbsolutePath(), Arrays.asList(projMock)));
    }

    @Test
    void multiple() {
        MavenProject projMock1 = mockProject("proj1");
        MavenProject projMock2 = mockProject("proj2");
        when(sessionMock.getAllProjects()).thenReturn(Arrays.asList(projMock1, projMock2));

        assertThat(underTest.createPathMap(sessionMock)).containsOnly(
                entry(Paths.get("proj1").toAbsolutePath(), Arrays.asList(projMock1)),
                entry(Paths.get("proj2").toAbsolutePath(), Arrays.asList(projMock2)));
    }

    @Test
    void multiple_twoWithSameDir() {
        MavenProject projMock1 = mockProject("proj1");
        MavenProject projMock2 = mockProject("proj2+3");
        MavenProject projMock3 = mockProject("proj2+3");
        when(sessionMock.getAllProjects()).thenReturn(Arrays.asList(projMock1, projMock2, projMock3));

        assertThat(underTest.createPathMap(sessionMock)).containsOnly(
                entry(Paths.get("proj1").toAbsolutePath(), Arrays.asList(projMock1)),
                entry(Paths.get("proj2+3").toAbsolutePath(), Arrays.asList(projMock2, projMock3)));
    }


    public MavenProject mockProject(String dirName) {
        MavenProject projMock = Mockito.mock(MavenProject.class);
        when(projMock.getBasedir()).thenReturn(new File(dirName));
        return projMock;
    }
}

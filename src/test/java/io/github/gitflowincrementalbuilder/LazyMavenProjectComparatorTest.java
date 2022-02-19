package io.github.gitflowincrementalbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gitflowincrementalbuilder.UnchangedProjectsRemover.LazyMavenProjectComparator;

@ExtendWith(MockitoExtension.class)
public class LazyMavenProjectComparatorTest {

    @Mock
    private MavenSession mavenSessionMock;

    @InjectMocks
    private LazyMavenProjectComparator underTest;

    @Test
    public void bothNull() {
        assertThat(underTest.compare(null, null)).isEqualTo(0);
    }

    @Test
    public void firstNull() {
        MavenProject second = new MavenProject();
        when(mavenSessionMock.getProjects()).thenReturn(Collections.singletonList(second));

        assertThat(underTest.compare(null, second)).isEqualTo(1);
    }

    @Test
    public void secondNull() {
        MavenProject first = new MavenProject();
        when(mavenSessionMock.getProjects()).thenReturn(Collections.singletonList(first));

        assertThat(underTest.compare(first, null)).isEqualTo(-1);
    }

    @Test
    public void firstBeforeSecond() {
        MavenProject first = projWithAid("first");
        MavenProject second = projWithAid("second");
        when(mavenSessionMock.getProjects()).thenReturn(Arrays.asList(first, second));

        assertThat(underTest.compare(first, second)).isEqualTo(-1);
    }

    @Test
    public void secondAfterFirst() {
        MavenProject first = projWithAid("first");
        MavenProject second = projWithAid("second");
        when(mavenSessionMock.getProjects()).thenReturn(Arrays.asList(first, second));

        assertThat(underTest.compare(second, first)).isEqualTo(1);
    }

    @Test
    public void same() {
        MavenProject first = projWithAid("first");
        MavenProject second = projWithAid("second");
        when(mavenSessionMock.getProjects()).thenReturn(Arrays.asList(first, second));

        assertThat(underTest.compare(first, first)).isEqualTo(0);
    }

    @Test
    public void indexMapIsRetained() {
        assertThat(underTest.compare(null, null)).isEqualTo(0);

        verify(mavenSessionMock).getProjects();

        assertThat(underTest.compare(null, null)).isEqualTo(0);

        verify(mavenSessionMock).getProjects(); // still only once, from the first call!
    }

    @Test
    public void nonReactorAfterReactor() {
        MavenProject nonReactor = projWithAid("nonReactor");
        MavenProject reactor = projWithAid("reactor");
        when(mavenSessionMock.getProjects()).thenReturn(Collections.singletonList(reactor));
        when(mavenSessionMock.getAllProjects()).thenReturn(Arrays.asList(nonReactor, reactor));

        assertThat(underTest.compare(nonReactor, reactor)).isEqualTo(1);
    }

    @Test
    public void secondNonReactoAfterFirstNonReactor() {
        MavenProject first = projWithAid("first");
        MavenProject second = projWithAid("second");
        when(mavenSessionMock.getAllProjects()).thenReturn(Arrays.asList(first, second));

        assertThat(underTest.compare(second, first)).isEqualTo(1);
    }

    private MavenProject projWithAid(String artifactId) {
        MavenProject proj = new MavenProject();
        proj.setArtifactId(artifactId);
        return proj;
    }
}

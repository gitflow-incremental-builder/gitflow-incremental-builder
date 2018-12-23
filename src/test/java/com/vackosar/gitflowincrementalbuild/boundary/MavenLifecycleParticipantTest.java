package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.hamcrest.core.IsSame;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.Properties;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MavenLifecycleParticipantTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Mock
    private Logger loggerMock;

    @Mock
    private MavenSession mavenSessionMock;

    @Mock
    private UnchangedProjectsRemover unchangedProjectsRemoverMock;

    @InjectMocks
    private MavenLifecycleParticipant underTest;

    private final Properties projectProperties = new Properties();

    @Before
    public void before() {
        MavenProject mockTLProject = mock(MavenProject.class);
        when(mockTLProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mockTLProject);

        when(mavenSessionMock.getRequest()).thenReturn(mock(MavenExecutionRequest.class));

        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(mock(ProjectDependencyGraph.class));

        Whitebox.setInternalState(underTest, new Configuration.Provider(mavenSessionMock));
    }

    @Test
    public void disabled() throws Exception {
        projectProperties.setProperty(Property.enabled.fullName(), "false");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerMock).info(contains("disabled"));
        verifyZeroInteractions(unchangedProjectsRemoverMock);
        verify(mavenSessionMock, never()).getProjectDependencyGraph();
    }

    @Test
    public void defaultlyEnabled() throws Exception {

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerMock).info(contains("starting..."));
        verify(unchangedProjectsRemoverMock).act();
    }

    @Test
    public void onRuntimeException() throws Exception {
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act();
        thrown.expect(MavenExecutionException.class);
        thrown.expectCause(IsSame.sameInstance(runtimeException));

        underTest.afterProjectsRead(mavenSessionMock);
    }

    @Test
    public void onRuntimeException_failOnErrorFalse() throws Exception {
        projectProperties.setProperty(Property.failOnError.fullName(), "false");
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act();

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerMock).info(contains(" skipped:"));
        verify(loggerMock).debug(anyString(), eq(runtimeException));
    }

    @Test
    public void onSkipExecutionException() throws Exception {
        SkipExecutionException skipExecutionException = new SkipExecutionException("FAIL !!!");
        doThrow(skipExecutionException).when(unchangedProjectsRemoverMock).act();

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerMock).info(contains(" skipped:"));
        verify(loggerMock).debug(anyString(), eq(skipExecutionException));
    }

    @Test
    public void projectDependencyGraphMissing() throws Exception {
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(null);

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerMock).warn(contains("ProjectDependencyGraph"));
        verifyZeroInteractions(unchangedProjectsRemoverMock);
    }
}

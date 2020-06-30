package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.LoggerSpyUtil;
import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitFactory;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MavenLifecycleParticipantTest {

    private static final String TEST_IMPL_VERSION = "3.8.1";    // just an existing version, no need to be the latest one

    private Logger loggerSpy = LoggerSpyUtil.buildSpiedLoggerFor(MavenLifecycleParticipant.class);

    @Mock(lenient = true)
    private MavenSession mavenSessionMock;

    @Mock
    private UnchangedProjectsRemover unchangedProjectsRemoverMock;

    @InjectMocks
    private MavenLifecycleParticipant underTest = new MavenLifecycleParticipant(TEST_IMPL_VERSION);

    private final Properties projectProperties = new Properties();

    @BeforeEach
    void before() {
        MavenProject mockTLProject = mock(MavenProject.class);
        when(mockTLProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mockTLProject);

        when(mavenSessionMock.getRequest()).thenReturn(mock(MavenExecutionRequest.class));

        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(mock(ProjectDependencyGraph.class));

        Whitebox.setInternalState(underTest, new Configuration.Provider(mavenSessionMock));
    }
    
    @AfterEach
    void after() {
        GitFactory.destroy();
    }

    @Test
    public void defaultlyEnabled() throws Exception {

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains("starting..."), eq(TEST_IMPL_VERSION));
        verify(unchangedProjectsRemoverMock).act();
    }

    @Test
    public void disabled() throws Exception {
        projectProperties.setProperty(Property.enabled.fullName(), "false");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains("disabled"));
        verifyNoInteractions(unchangedProjectsRemoverMock);
        verify(mavenSessionMock, never()).getProjectDependencyGraph();
    }

    @Test
    public void disabled_helpRequested() throws Exception {
        projectProperties.setProperty(Property.enabled.fullName(), "false");
        projectProperties.setProperty(Property.help.fullName(), "true");

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(true);
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    @Test
    public void helpRequested() throws Exception {
        projectProperties.setProperty(Property.help.fullName(), "true");

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(true);
        verify(unchangedProjectsRemoverMock).act();
    }

    @Test
    public void defaultlyNoHelp() throws Exception {

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(false);
    }

    @Test
    public void onRuntimeException() throws Exception {
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act();

        assertThatExceptionOfType(MavenExecutionException.class).isThrownBy(() -> underTest.afterProjectsRead(mavenSessionMock))
                .withCause(runtimeException);
    }

    @Test
    public void onRuntimeException_failOnErrorFalse() throws Exception {
        projectProperties.setProperty(Property.failOnError.fullName(), "false");
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act();

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains(" skipped:"), (Object) Mockito.any());
        verify(loggerSpy).debug(anyString(), eq(runtimeException));
    }

    @Test
    public void onSkipExecutionException() throws Exception {
        SkipExecutionException skipExecutionException = new SkipExecutionException("FAIL !!!");
        doThrow(skipExecutionException).when(unchangedProjectsRemoverMock).act();

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains(" skipped:"), (Object) Mockito.any());
        verify(loggerSpy).debug(anyString(), eq(skipExecutionException));
    }

    @Test
    public void projectDependencyGraphMissing() throws Exception {
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(null);

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).warn(contains("ProjectDependencyGraph"));
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    @Test
    public void enabledForBranch() throws Throwable {
        projectProperties.setProperty(Property.enabledBranchRegex.fullName(), "feature/.+");

        mockCurrentBranch("feature/cool-stuff");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(unchangedProjectsRemoverMock).act();
    }

    @Test
    public void disabledForBranch() throws Throwable {
        projectProperties.setProperty(Property.enabledBranchRegex.fullName(), "master");

        mockCurrentBranch("feature/cool-stuff");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info("gitflow-incremental-builder is disabled for this branch.");
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    private void mockCurrentBranch(String branchName) throws IOException {
        Git git = mock(Git.class);
        Repository repository = mock(Repository.class);
        doReturn(repository).when(git).getRepository();
        doReturn(branchName).when(repository).getBranch();
        
        GitFactory.bind(git);
    }

    private void verifyHelpLogged(boolean logged) {
        verify(loggerSpy, logged ? Mockito.times(1) : Mockito.never())
                .info(contains("help:"), eq(TEST_IMPL_VERSION), anyString(), anyString(), anyString());
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Properties;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import com.vackosar.gitflowincrementalbuild.LoggerSpyUtil;
import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

@ExtendWith(MockitoExtension.class)
public class MavenLifecycleParticipantTest {

    private static final String TEST_IMPL_VERSION = "3.8.1";    // just an existing version, no need to be the latest one

    private Logger loggerSpy = LoggerSpyUtil.buildSpiedLoggerFor(MavenLifecycleParticipant.class);

    @Mock(lenient = true)
    private MavenSession mavenSessionMock;

    @Mock
    private MavenExecutionRequest execRequestMock;

    @Mock
    private UnchangedProjectsRemover unchangedProjectsRemoverMock;

    @Mock
    private GitProvider gitProviderMock;

    @InjectMocks
    private MavenLifecycleParticipant underTest = new MavenLifecycleParticipant(TEST_IMPL_VERSION);

    private final Properties projectProperties = new Properties();

    @BeforeEach
    void before() {
        MavenProject currentProjectMock = mock(MavenProject.class);
        when(currentProjectMock.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getCurrentProject()).thenReturn(currentProjectMock);

        when(mavenSessionMock.getRequest()).thenReturn(execRequestMock);

        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(mock(ProjectDependencyGraph.class));
    }

    @Test
    public void defaultlyEnabled() throws MavenExecutionException {

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains("starting..."), eq(TEST_IMPL_VERSION));
        verify(unchangedProjectsRemoverMock).act(any(Configuration.class));
    }

    @Test
    public void disabled() throws MavenExecutionException {
        projectProperties.setProperty(Property.disable.prefixedName(), "true");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains("disabled"));
        verifyNoInteractions(unchangedProjectsRemoverMock);
        verify(mavenSessionMock, never()).getProjectDependencyGraph();
    }

    @Test
    public void disabled_helpRequested() throws MavenExecutionException {
        projectProperties.setProperty(Property.disable.prefixedName(), "true");
        projectProperties.setProperty(Property.help.prefixedName(), "true");

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(true);
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    @Test
    public void helpRequested() throws MavenExecutionException {
        projectProperties.setProperty(Property.help.prefixedName(), "true");

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(true);
        verify(unchangedProjectsRemoverMock).act(any(Configuration.class));
    }

    @Test
    public void defaultlyNoHelp() throws MavenExecutionException {

        underTest.afterProjectsRead(mavenSessionMock);

        verifyHelpLogged(false);
    }

    @Test
    public void onRuntimeException() throws MavenExecutionException {
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act(any(Configuration.class));

        assertThatExceptionOfType(MavenExecutionException.class).isThrownBy(() -> underTest.afterProjectsRead(mavenSessionMock))
                .withCause(runtimeException);
    }

    @Test
    public void onRuntimeException_failOnErrorFalse() throws MavenExecutionException {
        projectProperties.setProperty(Property.failOnError.prefixedName(), "false");
        RuntimeException runtimeException = new RuntimeException("FAIL !!!");
        doThrow(runtimeException).when(unchangedProjectsRemoverMock).act(any(Configuration.class));

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains(" skipped:"), (Object) Mockito.any());
        verify(loggerSpy).debug(anyString(), eq(runtimeException));
    }

    @Test
    public void onSkipExecutionException() throws MavenExecutionException {
        SkipExecutionException skipExecutionException = new SkipExecutionException("FAIL !!!");
        doThrow(skipExecutionException).when(unchangedProjectsRemoverMock).act(any(Configuration.class));

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info(contains(" skipped:"), (Object) Mockito.any());
        verify(loggerSpy).debug(anyString(), eq(skipExecutionException));
    }

    @Test
    public void projectDependencyGraphMissing() throws MavenExecutionException {
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(null);

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).warn(contains("ProjectDependencyGraph"));
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    @Test
    public void enabledForReferenceBranch() throws MavenExecutionException {
        projectProperties.setProperty(Property.disableIfReferenceBranchMatches.prefixedName(), "origin/\\d+\\.\\d+");

        projectProperties.setProperty(Property.referenceBranch.prefixedName(), "feature/group-effort");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(unchangedProjectsRemoverMock).act(any(Configuration.class));
    }

    @Test
    public void disabledForReferenceBranch() throws MavenExecutionException {
        projectProperties.setProperty(Property.disableIfReferenceBranchMatches.prefixedName(), "origin/\\d+\\.\\d+");

        projectProperties.setProperty(Property.referenceBranch.prefixedName(), "origin/1.13");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info("gitflow-incremental-builder is disabled for reference branch: {}", "origin/1.13");
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    @Test
    public void enabledForCurrentBranch() throws MavenExecutionException {
        projectProperties.setProperty(Property.disableIfBranchMatches.prefixedName(), "main|develop|(release/.+)|(hotfix/.+)");

        mockCurrentBranch("feature/cool-stuff");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(unchangedProjectsRemoverMock).act(any(Configuration.class));
    }

    @Test
    public void enabledForCurrentBranch_quarkusStyle() throws MavenExecutionException {
        projectProperties.setProperty(Property.disableIfBranchMatches.prefixedName(), "main|\\d+\\.\\d+|.*backport.*");

        mockCurrentBranch("bump-xyz-to-1.1");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(unchangedProjectsRemoverMock).act(any(Configuration.class));
    }

    @Test
    public void disabledForCurrentBranch() throws MavenExecutionException {
        projectProperties.setProperty(Property.disableIfBranchMatches.prefixedName(), "main|develop|(release/.+)|(hotfix/.+)");

        mockCurrentBranch("develop");

        underTest.afterProjectsRead(mavenSessionMock);

        verify(loggerSpy).info("gitflow-incremental-builder is disabled for the current branch: {}", "develop");
        verifyNoInteractions(unchangedProjectsRemoverMock);
    }

    private void mockCurrentBranch(String branchName) {
        when(gitProviderMock.getCurrentBranch(any(Configuration.class))).thenReturn(branchName);
    }

    private void verifyHelpLogged(boolean logged) {
        verify(loggerSpy, logged ? Mockito.times(1) : Mockito.never())
                .info(contains("help:"), eq(TEST_IMPL_VERSION), anyString(), anyString(), anyString());
    }

    // ////////////////////////////////////
    // warnIfBuggyOrUnsupportedMavenVersion

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_null() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion(null, new Configuration(mavenSessionMock));

        verify(loggerSpy).warn(contains("Could not get Maven version"));
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_381() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.8.1", new Configuration(mavenSessionMock));

        verifyNoInteractions(loggerSpy);
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_363() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.6.3", new Configuration(mavenSessionMock));

        verifyNoInteractions(loggerSpy);
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_354() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.5.4", new Configuration(mavenSessionMock));

        verifyNoInteractions(loggerSpy);
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_330() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.3.0", new Configuration(mavenSessionMock));

        verifyNoInteractions(loggerSpy);
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_339() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.3.9", new Configuration(mavenSessionMock));

        verifyNoInteractions(loggerSpy);
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_339_withSelectedProject() {
        when(execRequestMock.getSelectedProjects()).thenReturn(Collections.singletonList("foo"));

        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.3.9", new Configuration(mavenSessionMock));

        verify(loggerSpy).warn(contains("MNG-6173"), eq("3.3.9"));
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_339_withDisableSelectedProjectsHandling() {
        projectProperties.setProperty(Property.disableSelectedProjectsHandling.prefixedName(), "true");

        underTest.warnIfBuggyOrUnsupportedMavenVersion("3.3.9", new Configuration(mavenSessionMock));

        verify(loggerSpy).warn(contains("MNG-6173"), eq("3.3.9"));
    }

    @Test
    public void warnIfBuggyOrUnsupportedMavenVersion_400() {
        underTest.warnIfBuggyOrUnsupportedMavenVersion("4.0.0", new Configuration(mavenSessionMock));

        verify(loggerSpy).warn(contains("not tested"), eq("4.0.0"));
    }
}

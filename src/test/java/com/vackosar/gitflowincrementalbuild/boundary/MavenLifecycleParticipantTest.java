package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class MavenLifecycleParticipantTest extends BaseRepoTest {

    private MavenLifecycleParticipant participant;
    private StringBuilder builder;

    @Before
    public void before() throws Exception {
        super.before();
        Logger logger = mock(Logger.class);
        participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, logger);
        builder = new StringBuilder();
        Answer<?> loggingAnswer = invocation -> builder.append(invocation.getArguments()[0]).append("\n");
        doAnswer(loggingAnswer).when(logger).warn(anyString());
        doAnswer(loggingAnswer).when(logger).info(anyString());
        doAnswer(invocation -> builder).when(logger).debug(anyString());
    }

    @Test
    public void disabled() throws Exception {
        Property.enabled.setValue("false");
        MavenSession session = getMavenSessionMock();

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder is disabled."));
        verify(session).getTopLevelProject();
        verifyNoMoreInteractions(session);
    }

    @Test
    public void disabledOverridingPom() throws Exception {
        Property.enabled.setValue("false");
        MavenSession session = getMavenSessionMock();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "true");

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder is disabled."));
        verify(session, times(2)).getTopLevelProject();
        verifyNoMoreInteractions(session);
    }

    @Test
    public void disabledInPom() throws Exception {
        Property.enabled.setValue(null);
        MavenSession session = getMavenSessionMock();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "false");

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder is disabled."));
        verify(session, times(2)).getTopLevelProject();
        verifyNoMoreInteractions(session);
    }

    @Test
    public void defaultlyEnabled() throws Exception {
        MavenSession session = getMavenSessionMock();

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder starting..."));
    }

    @Test
    public void failOnErrorFalse() throws Exception {
        Property.failOnError.setValue("false");
        MavenSession session = getMavenSessionMock();
        when(session.getCurrentProject()).thenThrow(new RuntimeException("FAIL !!!"));

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder execution skipped:"));
        Assert.assertTrue(builder.toString().contains("FAIL !!!"));
    }

    @Test
    public void skipOnSkippable() throws Exception {
        MavenSession session = getMavenSessionMock();
        when(session.getCurrentProject()).thenThrow(new SkipExecutionException("FAIL !!!"));

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder execution skipped:"));
        Assert.assertTrue(builder.toString().contains("SkipExecutionException"));
        Assert.assertTrue(builder.toString().contains("FAIL !!!"));
    }

    @Test
    public void projectDependencyGraphMissing() throws Exception {
        MavenSession session = getMavenSessionMock();
        when(session.getProjectDependencyGraph()).thenReturn(null);

        participant.afterProjectsRead(session);

        Assert.assertTrue(builder.toString().contains("ProjectDependencyGraph"));
        verify(session).getTopLevelProject();
        verify(session).getProjectDependencyGraph();
        verifyNoMoreInteractions(session);
    }
}

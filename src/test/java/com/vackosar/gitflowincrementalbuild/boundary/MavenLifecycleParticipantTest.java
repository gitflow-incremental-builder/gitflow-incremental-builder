package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MavenLifecycleParticipantTest extends BaseRepoTest {

    @Test
    public void disabled() throws Exception {
        Property.enabled.setValue("false");
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        participant.afterProjectsRead(getMavenSessionMock());
    }

    @Test
    public void disabledOverridingPom() throws Exception {
        Property.enabled.setValue("false");
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        MavenSession session = getMavenSessionMock();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "true");
        participant.afterProjectsRead(session);
    }

    @Test
    public void disabledInPom() throws Exception {
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        MavenSession session = getMavenSessionMock();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "false");
        participant.afterProjectsRead(session);
    }

    @Test
    public void defaultlyEnabled() throws Exception {
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        Logger logger = mock(Logger.class);
        loggerField.set(participant, logger);
        StringBuilder builder = new StringBuilder();
        doAnswer(invocation -> builder.append(invocation.getArguments()[0]).append("\n")).when(logger).info(anyString());
        MavenSession session = getMavenSessionMock();
        participant.afterProjectsRead(session);
        Assert.assertTrue(builder.toString().contains("gitflow-incremental-builder starting..."));
    }

}

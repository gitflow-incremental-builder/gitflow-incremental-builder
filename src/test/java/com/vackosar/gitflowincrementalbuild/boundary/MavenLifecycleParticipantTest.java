package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;

public class MavenLifecycleParticipantTest {

    @Test public void disabled() throws Exception {
        Property.enabled.setValue("false");
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        participant.afterProjectsRead(MavenSessionMock.get());
    }

    @Test public void disabledOverridingPom() throws Exception {
        Property.enabled.setValue("false");
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        MavenSession session = MavenSessionMock.get();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "true");
        participant.afterProjectsRead(session);
    }

    @Test public void disabledInPom() throws Exception {
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        MavenSession session = MavenSessionMock.get();
        session.getTopLevelProject().getProperties().setProperty(Property.enabled.fullName(), "false");
        participant.afterProjectsRead(session);
    }

    @Test(expected = MavenExecutionException.class)
    public void defaultlyEnabled() throws Exception {
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, mock(Logger.class));
        MavenSession session = MavenSessionMock.get();
        participant.afterProjectsRead(session);
    }

}

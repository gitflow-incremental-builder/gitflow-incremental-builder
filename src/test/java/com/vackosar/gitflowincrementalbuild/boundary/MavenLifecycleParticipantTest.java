package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.MavenExecutionException;
import org.codehaus.plexus.logging.Logger;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

public class MavenLifecycleParticipantTest {

    @Test public void disabled() throws MavenExecutionException, NoSuchFieldException, IllegalAccessException {
        Property.enabled.setValue("false");
        MavenLifecycleParticipant participant = new MavenLifecycleParticipant();
        Field loggerField = participant.getClass().getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(participant, Mockito.mock(Logger.class));
        participant.afterProjectsRead(null);
    }

}

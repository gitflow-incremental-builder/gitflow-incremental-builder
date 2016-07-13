package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    @Requirement private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        try {
            if (!Boolean.valueOf(Property.enabled.getValue())) {
                logger.info("gitflow-incremental-builder is disabled.");
            } else {
                logger.info("gitflow-incremental-builder is enabled.");
                Guice
                        .createInjector(new GuiceModule(logger, session))
                        .getInstance(UnchangedProjectsRemover.class)
                        .act();
            }
        } catch (Exception e) {
            throw new MavenExecutionException("Exception", e);
        }
    }


}

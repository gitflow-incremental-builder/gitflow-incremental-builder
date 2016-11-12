package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    @Requirement private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        try {
            mergeCurrentProjectProperties(session);
            if (Boolean.valueOf(Property.enabled.getValue())) {
                logger.info("gitflow-incremental-builder starting..."); //TODO Print version.
                execute(session);
                logger.info("gitflow-incremental-builder exiting...");
            } else {
                logger.info("gitflow-incremental-builder is disabled.");
            }
        } catch (Exception e) {
            throw new MavenExecutionException("Exception during gitflow-incremental-builder execution occured.", e);
        }
    }

    private void execute(MavenSession session) throws GitAPIException, IOException {
        Guice
                .createInjector(new GuiceModule(logger, session))
                .getInstance(UnchangedProjectsRemover.class)
                .act();
    }

    private void mergeCurrentProjectProperties(MavenSession mavenSession) {
        mavenSession.getTopLevelProject().getProperties().entrySet().stream()
                .filter(e->e.getKey().toString().startsWith(Property.PREFIX))
                .filter(e->System.getProperty(e.getKey().toString()) == null)
                .forEach(e->System.setProperty(e.getKey().toString(), e.getValue().toString()));
    }

}

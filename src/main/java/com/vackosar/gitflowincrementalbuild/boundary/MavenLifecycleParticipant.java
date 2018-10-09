package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Guice;
import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
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
        mergeCurrentProjectProperties(session);

        if (!Boolean.valueOf(Property.enabled.getValue())) {
            logger.info("gitflow-incremental-builder is disabled.");
            return;
        }

        // check prerequisites
        if (session.getProjectDependencyGraph() == null) {
            logger.warn("Execution of gitflow-incremental-builder is not supported in this environment: "
                    + "Current MavenSession does not provide a ProjectDependencyGraph. "
                    + "Consider disabling gitflow-incremental-builder via property: " + Property.enabled.fullName());
            return;
        }

        logger.info("gitflow-incremental-builder starting..."); //TODO Print version.
        try {
            execute(session);
        } catch (Exception e) {
            Boolean failOnError = Boolean.valueOf(Property.failOnError.getValue());
            if (! failOnError || (e.getMessage() != null && e.getMessage().contains(SkipExecutionException.class.getCanonicalName()))) {
                logger.info("gitflow-incremental-builder execution skipped: " + e.toString());
                logger.debug("Full exception:", e);
            } else {
                throw new MavenExecutionException("Exception during gitflow-incremental-builder execution occurred.", e);
            }
        }
        logger.info("gitflow-incremental-builder exiting...");
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

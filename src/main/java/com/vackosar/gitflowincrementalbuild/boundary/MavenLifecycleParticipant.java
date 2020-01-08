package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private Logger logger = LoggerFactory.getLogger(MavenLifecycleParticipant.class);

    @Inject private UnchangedProjectsRemover unchangedProjectsRemover;

    @Inject private Configuration.Provider configProvider;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {

        if (!Configuration.isEnabled(session)) {
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

        logger.info("gitflow-incremental-builder " + getClass().getPackage().getImplementationVersion() + " starting...");
        try {
            unchangedProjectsRemover.act();
        } catch (Exception e) {
            boolean isSkipExecException = e instanceof SkipExecutionException;
            if (!configProvider.get().failOnError || isSkipExecException) {
                logger.info("gitflow-incremental-builder execution skipped: {}", (isSkipExecException ? e.getMessage() : e.toString()));
                logger.debug("Full exception:", e);
            } else {
                throw new MavenExecutionException("Exception during gitflow-incremental-builder execution occurred.", e);
            }
        }
        logger.info("gitflow-incremental-builder exiting...");
    }
}

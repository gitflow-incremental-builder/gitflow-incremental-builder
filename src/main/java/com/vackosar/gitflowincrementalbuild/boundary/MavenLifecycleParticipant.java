package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;

@Singleton
@Named
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private Logger logger = LoggerFactory.getLogger(MavenLifecycleParticipant.class);

    @Inject private UnchangedProjectsRemover unchangedProjectsRemover;

    @Inject private GitProvider gitProvider;

    private final String implVersion;

    public MavenLifecycleParticipant() {
        implVersion = getClass().getPackage().getImplementationVersion();
    }

    // only for testing!
    MavenLifecycleParticipant(String implVersion) {
        this.implVersion = implVersion;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {

        final Configuration config = new Configuration(session);

        if (config.help) {
            logHelp();
        }

        if (config.disable) {
            logger.info("gitflow-incremental-builder is disabled.");
            return;
        }

        // check prerequisites
        if (session.getProjectDependencyGraph() == null) {
            logger.warn("Execution of gitflow-incremental-builder is not supported in this environment: "
                    + "Current MavenSession does not provide a ProjectDependencyGraph. "
                    + "Consider disabling gitflow-incremental-builder via property '" + Property.disable.name() + "'.");
            return;
        }

        try {
            perform(config);
        } finally {
            gitProvider.close();
        }
    }

    private void perform(Configuration config) throws MavenExecutionException {

        try {
            if (isDisabledForBranch(config)) {
                logger.info("gitflow-incremental-builder is disabled for this branch.");
                return;
            }

            logger.info("gitflow-incremental-builder {} starting...", implVersion);
            unchangedProjectsRemover.act(config);
        } catch (Exception e) {
            boolean isSkipExecException = e instanceof SkipExecutionException;
            if (!config.failOnError || isSkipExecException) {
                logger.info("gitflow-incremental-builder execution skipped: {}", (isSkipExecException ? e.getMessage() : e.toString()));
                logger.debug("Full exception:", e);
            } else {
                throw new MavenExecutionException("Exception during gitflow-incremental-builder execution occurred.", e);
            }
        }
        logger.info("gitflow-incremental-builder exiting...");
    }

    private boolean isDisabledForBranch(Configuration config) {
        return config.disableIfBranchRegex.map(predicate -> {
            try {
                return predicate.test(gitProvider.get(config).getRepository().getBranch());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).orElse(false);
    }

    private void logHelp() {
        logger.info("gitflow-incremental-builder {} help:\n{}\nFor more help see: {}/tree/version/{}#configuration\n",
                implVersion,
                Property.exemplifyAll(),
                "https://github.com/vackosar/gitflow-incremental-builder",
                implVersion);
    }
}

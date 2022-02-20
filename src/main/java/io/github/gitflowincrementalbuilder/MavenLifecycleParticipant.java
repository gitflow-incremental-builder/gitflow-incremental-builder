package io.github.gitflowincrementalbuilder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

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

    private void logHelp() {
        logger.info("gitflow-incremental-builder {} help:\n{}\nFor more help see: {}/tree/v{}#configuration\n",
                implVersion,
                Property.exemplifyAll(),
                "https://github.com/gitflow-incremental-builder/gitflow-incremental-builder",
                implVersion);
    }

    private void perform(Configuration config) throws MavenExecutionException {

        try {
            if (isDisabledForReferenceBranch(config) || isDisabledForCurrentBranch(config)) {
                return;
            }

            logger.info("gitflow-incremental-builder {} starting...", implVersion);
            warnIfBuggyOrUnsupportedMavenVersion(MavenSession.class.getPackage().getImplementationVersion(), config);
            unchangedProjectsRemover.act(config);
        } catch (Exception e) {
            boolean isSkipExecException = e instanceof SkipExecutionException;
            if (!config.failOnError || isSkipExecException) {
                logger.info("gitflow-incremental-builder execution skipped: {}", (isSkipExecException ? e.getMessage() : e.toString()));
                logger.debug("Full exception:", e);
            } else {
                throw new MavenExecutionException("Failed to execute gitflow-incremental-builder.", e);
            }
        }
        logger.info("gitflow-incremental-builder exiting...");
    }

    private boolean isDisabledForReferenceBranch(Configuration config) {
        boolean matches = config.disableIfReferenceBranchMatches.map(predicate -> predicate.test(config.referenceBranch)).orElse(false);
        if (matches) {
            logger.info("gitflow-incremental-builder is disabled for reference branch: {}", config.referenceBranch);
        }
        return matches;
    }

    private boolean isDisabledForCurrentBranch(Configuration config) {
        return config.disableIfBranchMatches.map(predicate -> {
            String branch = gitProvider.getCurrentBranch(config);
            boolean matches = predicate.test(branch);
            if (matches) {
                logger.info("gitflow-incremental-builder is disabled for the current branch: {}", branch);
            }
            return matches;
        }).orElse(false);
    }

    void warnIfBuggyOrUnsupportedMavenVersion(String mavenVersion, Configuration config) {
        if (mavenVersion == null) {
            logger.warn("Could not get Maven version.");
        } else if (!mavenVersion.startsWith("3.8.") && !mavenVersion.equals("3.6.3")) {
            logger.warn("Detected Maven {} was not tested with gitflow-incremental-builder.", mavenVersion);
        }
    }
}

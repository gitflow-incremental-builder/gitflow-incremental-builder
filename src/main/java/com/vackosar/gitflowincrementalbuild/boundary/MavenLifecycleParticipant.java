package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitFactory;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;

@Singleton
@Named
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private Logger logger = LoggerFactory.getLogger(MavenLifecycleParticipant.class);

    @Inject private UnchangedProjectsRemover unchangedProjectsRemover;

    @Inject private Configuration.Provider configProvider;

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
        try {
            applyPlugin(session);
        } finally {
            GitFactory.destroy();
        }
    }

    private void applyPlugin(MavenSession session) throws MavenExecutionException {

        if (Configuration.isHelpRequested(session)) {
            logHelp();
        }

        if (!Configuration.isEnabled(session)) {
            logger.info("gitflow-incremental-builder is disabled.");
            return;
        }

        // check prerequisites
        if (session.getProjectDependencyGraph() == null) {
            logger.warn("Execution of gitflow-incremental-builder is not supported in this environment: "
                    + "Current MavenSession does not provide a ProjectDependencyGraph. "
                    + "Consider disabling gitflow-incremental-builder via property: " + Property.enabled.fullOrShortName());
            return;
        }

        try {
            if (isDisabledForBranch(session)) {
                logger.info("gitflow-incremental-builder is disabled for this branch.");
                return;
            }

            logger.info("gitflow-incremental-builder {} starting...", implVersion);
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

    private boolean isDisabledForBranch(MavenSession session) throws IOException {
        Configuration configuration = configProvider.get();
        if (!configuration.disableIfBranchRegex.isPresent()) {
            return false;
        }
        
        Git git = GitFactory.getOrCreateThreadLocalGit(session, configuration);
        String branchName = git.getRepository().getBranch();
        return configuration.disableIfBranchRegex.get().test(branchName);
    }

    private void logHelp() {
        logger.info("gitflow-incremental-builder {} help:\n{}\nFor more help see: {}/tree/version/{}#configuration\n",
                implVersion,
                Property.exemplifyAll(),
                "https://github.com/vackosar/gitflow-incremental-builder",
                implVersion);
    }
}

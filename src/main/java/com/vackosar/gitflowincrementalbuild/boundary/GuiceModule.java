package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.impl.StaticLoggerBinder;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GuiceModule extends AbstractModule {

    public static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";
    private final Logger logger;
    private final MavenSession mavenSession;

    public GuiceModule(Logger logger, MavenSession mavenSession) {
        this.logger = logger;
        this.mavenSession = mavenSession;
    }

    @Provides
    @Singleton
    public Git provideGit(final StaticLoggerBinder staticLoggerBinder, final Configuration configuration) throws IOException, GitAPIException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = mavenSession.getCurrentProject().getBasedir().toPath().toFile();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            String gitDirNotFoundMessage = "Git repository root directory not found ascending from current working directory:'" + pomDir + "'.";
            logger.warn(gitDirNotFoundMessage + " Next step is determined by failOnMissingGitDir property.");
            if (configuration.failOnMissingGitDir) {
                throw new IllegalArgumentException(gitDirNotFoundMessage);
            } else {
                throw new SkipExecutionException(gitDirNotFoundMessage);
            }
        }
        if (isWorktree(builder)) {
            throw new SkipExecutionException(UNSUPPORTED_WORKTREE + builder.getGitDir());
        }
        logger.info("Git dir is: " + String.valueOf(builder.getGitDir().getAbsolutePath()));
        return Git.wrap(builder.build());
    }

    private void reconfigureForWorktree(Configuration configuration, FileRepositoryBuilder builder) throws IOException {

        Path worktreeGitDir = builder.getGitDir().toPath().normalize().toAbsolutePath();
        logger.info("Separate worktree checkout detected from current git dir: " + worktreeGitDir);
        builder.setWorkTree(worktreeGitDir.resolve(Files.lines(worktreeGitDir.resolve("gitdir")).findAny().get()).normalize().toAbsolutePath().toFile());
        logger.info("Git worktree dir is: " + builder.getWorkTree());
        builder.setGitDir(worktreeGitDir.resolve(Files.lines(worktreeGitDir.resolve("commondir")).findAny().get()).normalize().toAbsolutePath().toFile());
        if (configuration.baseBranch.equals("HEAD")) {
            String fixedHeadRef = "worktrees/" + worktreeGitDir.getFileName().toString() + "/HEAD";
            logger.info("Replacing HEAD with " + fixedHeadRef + " to compensate for worktree usage.");
            configuration.baseBranch = fixedHeadRef;
        }
    }

    private boolean isWorktree(FileRepositoryBuilder builder) {
        return builder.getGitDir().toPath().getParent().getFileName().toString().equals("worktrees")
                && builder.getGitDir().toPath().getParent().getParent().getFileName().toString().equals(".git");
    }

    @Provides @Singleton public MavenSession provideMavenSession() { return mavenSession; }

    @Provides @Singleton public Logger provideLogger() { return logger; }

    @Override
    protected void configure() {}

}

package com.vackosar.gitflowincrementalbuild.control.jgit;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

@Singleton
@Named
public class GitProvider implements Provider<Git> {

    private static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private Logger logger = LoggerFactory.getLogger(GitProvider.class);

    private final MavenSession mavenSession;
    private final Configuration configuration;

    private Git git;

    @Inject
    public GitProvider(MavenSession mavenSession, Configuration configuration) {
        this.mavenSession = mavenSession;
        this.configuration = configuration;
    }

    /**
     * Returns a {@link Git} instance which is constructed when first called. Subsequent calls will return the same instance.
     *
     * @return a {@link Git} instance
     */
    @Override
    public Git get() {
        if (git == null) {
            try {
                git = setupGit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return git;
    }

    public void close() {
        if (git != null) {
            git.close();
            git.getRepository().close();
        }
    }

    private Git setupGit() throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = mavenSession.getCurrentProject().getBasedir().toPath().toFile();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            String gitDirNotFoundMessage = "Git repository root directory not found ascending from current working directory:'"
                    + pomDir + "'.";
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
        return Git.wrap(builder.build());
    }

    private static boolean isWorktree(FileRepositoryBuilder builder) {
        return Optional.ofNullable(builder.getGitDir().toPath().getParent())
                .filter(parent -> parent.getFileName().toString().equals("worktrees"))
                .map(Path::getParent)
                .filter(Objects::nonNull)
                .map(parentParent -> parentParent.getFileName().toString().equals(".git"))
                .orElse(false);
    }
}

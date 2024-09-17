package com.vackosar.gitflowincrementalbuild.control.jgit;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Singleton
@Named
public class GitProvider {

    private static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private Logger logger = LoggerFactory.getLogger(GitProvider.class);

    private Git git;

    /**
     * Returns a {@link Git} instance which is constructed when first called. Subsequent calls will return the same instance.
     *
     * @param config the configuration
     * @return a {@link Git} instance
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Makes no sense to wrap 'git', it's only for internal use anyway.")
    public Git get(Configuration config) {
        if (git == null) {
            try {
                git = setupGit(config);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return git;
    }

    public Path getProjectRoot(Configuration config) {
        return get(config).getRepository().getDirectory().toPath().getParent();
    }

    public String getCurrentBranch(Configuration config) {
        try {
            return get(config).getRepository().getBranch();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void close() {
        if (git != null) {
            git.close();
            git.getRepository().close();
        }
    }

    private Git setupGit(Configuration config) throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = config.currentProject.getBasedir();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            String gitDirNotFoundMessage = "Git repository root directory not found ascending from current working directory:'"
                    + pomDir + "'.";
            logger.warn(gitDirNotFoundMessage + " Next step is determined by failOnMissingGitDir property.");
            if (config.failOnMissingGitDir) {
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

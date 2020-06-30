package com.vackosar.gitflowincrementalbuild.control.jgit;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class GitFactory {

    private static final Logger logger = LoggerFactory.getLogger(GitFactory.class);

    private static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private static final ThreadLocal<Git> threadLocal = new ThreadLocal<>();
    
    public static Git getOrCreateThreadLocalGit(MavenSession mavenSession, Configuration configuration) throws IOException {
        if (threadLocal.get() != null) {
            return threadLocal.get();
        }
        Git git = setupGit(mavenSession, configuration);
        threadLocal.set(git);
        return git;
    }
    
    public static void bind(Git git) {
        destroy();
        threadLocal.set(git);
    }

    public static void destroy() {
        Git git = threadLocal.get();
        threadLocal.remove();
        if (git != null) {
            git.close();
            git.getRepository().close();
        }
    }

    private static Git setupGit(MavenSession mavenSession, Configuration configuration) throws IOException {
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

    private GitFactory() {
        throw new AssertionError();
    }
}
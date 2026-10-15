package io.github.gitflowincrementalbuilder.jgit;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.SkipExecutionException;
import io.github.gitflowincrementalbuilder.config.Configuration;

@Singleton
@Named
public class GitProvider {

    private Logger logger = LoggerFactory.getLogger(GitProvider.class);

    private Git git;

    /**
     * Returns a {@link Git} instance which is constructed when first called. Subsequent calls will return the same instance until {@link #close()}.
     *
     * @param config the configuration
     * @return a {@link Git} instance
     */
    Git get(Configuration config) {
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
        return get(config).getRepository().getWorkTree().toPath();
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
            try {
                git.close();
                git.getRepository().close();
            } finally {
                // this singleton outlives the build in long-lived JVMs (e.g. mvnd), the next build can be in another repository
                git = null;
            }
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
        return Git.wrap(builder.build());
    }
}

package com.vackosar.gitflowincrementalbuild.mocks;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class EmptyLocalRepoMock implements AutoCloseable {

    static {
        JGitIsolation.ensureIsolatedFromSystemAndUserConfig();
    }

    private final File baseFolder;
    private final Git git;

    public EmptyLocalRepoMock(File allReposBaseFolder) throws IOException, URISyntaxException, GitAPIException {
        this.baseFolder = new File(allReposBaseFolder.getAbsolutePath(), "tmp/emptyrepo/");
        this.baseFolder.mkdirs();
        
        git = Git.init().setDirectory(baseFolder).call();
    }

    public Git getGit() {
        return git;
    }

    @Override
    public void close() {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
    }

    public File getBaseCanonicalBaseFolder() throws IOException {
        return baseFolder.getCanonicalFile();
    }
}

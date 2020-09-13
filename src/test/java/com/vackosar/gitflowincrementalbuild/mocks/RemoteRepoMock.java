package com.vackosar.gitflowincrementalbuild.mocks;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;

import com.vackosar.gitflowincrementalbuild.mocks.server.TestServer;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class RemoteRepoMock implements AutoCloseable {

    static {
        JGitIsolation.ensureIsolatedFromSystemAndUserConfig();
    }

    public final URI repoUri;

    private final Git git;
    private final Path repoDir;
    private final TestServer testServer;

    public RemoteRepoMock(Path baseDir, TestServerType testServerType) throws IOException {
        this(baseDir, false, testServerType);
    }

    public RemoteRepoMock(Path baseDir, boolean bare, TestServerType testServerType) throws IOException {
        this.repoDir = baseDir.resolve("tmp/remote");

        if (bare) {
            Files.createDirectories(repoDir);
        } else {
            unpackTemplateProject();
        }

        try {
            git = new Git(new FileRepository(repoDir.resolve(".git").toFile()));
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }

        testServer = testServerType.buildServer();
        repoUri = testServer.start(git.getRepository());
    }

    private void unpackTemplateProject() {
        new UnZipper().act(UnZipper.TEMPLATE_PROJECT_ZIP, repoDir);
    }

    @Override
    public void close() {
        if (testServer != null) {
            testServer.stop();
        }
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
    }

    public Git getGit() {
        return git;
    }
}

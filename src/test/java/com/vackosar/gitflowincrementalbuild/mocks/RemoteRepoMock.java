package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;

import com.vackosar.gitflowincrementalbuild.mocks.server.TestServer;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

import java.io.File;
import java.io.IOException;

public class RemoteRepoMock implements AutoCloseable {

    static {
        JGitIsolation.ensureIsolatedFromSystemAndUserConfig();
    }

    public final String repoUrl;

    private final Git git;
    private final File repoFolder;
    private final File templateProjectZip = new File(getClass().getClassLoader().getResource("template.zip").getFile());
    private final TestServer testServer;

    public RemoteRepoMock(File baseFolder, TestServerType testServerType) throws IOException {
        this(baseFolder, false, testServerType);
    }

    public RemoteRepoMock(File baseFolder, boolean bare, TestServerType testServerType) throws IOException {
        this.repoFolder = new File(baseFolder, "tmp/remote");

        if (bare) {
            repoFolder.mkdirs();
        } else {
            unpackTemplateProject();
        }

        testServer = testServerType.buildServer();
        repoUrl = testServer.start(repoFolder);
        try {
            git = new Git(new FileRepository(new File(repoFolder, ".git")));
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    private void unpackTemplateProject() {
        new UnZipper().act(templateProjectZip, repoFolder);
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

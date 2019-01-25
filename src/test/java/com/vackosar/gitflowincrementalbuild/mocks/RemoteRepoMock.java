package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.transport.Daemon;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class RemoteRepoMock implements AutoCloseable {

    static {
        JGitIsolation.ensureIsolatedFromSystemAndUserConfig();
    }

    public final String repoUrl;

    private final Git git;
    private final File repoFolder;
    private final File templateProjectZip = new File(getClass().getClassLoader().getResource("template.zip").getFile());
    private final Daemon server;

    public RemoteRepoMock(File baseFolder, boolean bare) throws IOException {
        this.repoFolder = new File(baseFolder, "tmp/remote");

        if (bare) {
            repoFolder.mkdirs();
        } else {
            unpackTemplateProject();
        }

        server = start(repoFolder, bare);
        repoUrl = String.format("git://%s:%s/repo.git", server.getAddress().getHostName(), server.getAddress().getPort());
        git = new Git(new FileRepository(new File(repoFolder, ".git")));
    }

    private static Daemon start(File repoFolder, boolean bare) {
        try {
            Daemon server = new Daemon(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.getService("git-receive-pack").setEnabled(true);
            server.setRepositoryResolver(new RepoResolver(repoFolder, bare));
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start JGit daemon for repo at: " + repoFolder, e);
        }
    }

    private void unpackTemplateProject() {
        new UnZipper().act(templateProjectZip, repoFolder);
    }

    @Override
    public void close() throws Exception {
        server.stop();
        git.getRepository().close();
        git.close();
    }

    public Git getGit() {
        return git;
    }
}

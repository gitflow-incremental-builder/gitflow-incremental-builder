package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.transport.Daemon;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;

public class RemoteRepoMock implements AutoCloseable {
    private static int port = 9000 + new Random().nextInt(500);
    private final Git git;
    public String repoUrl;

    private File repoFolder;
    private File templateProjectZip = new File(getClass().getClassLoader().getResource("template.zip").getFile());
    private boolean bare;
    private Daemon server;
    private RepoResolver resolver;

    public RemoteRepoMock(File baseFolder, boolean bare) throws IOException {
        this.bare = bare;
        this.repoFolder = new File(baseFolder, "tmp/remote");

        if (bare) {
            repoFolder.mkdirs();
        } else {
            unpackTemplateProject();
        }

        repoUrl = "git://localhost:" + port + "/repo.git";
        start();
        port++;
        git = new Git(new FileRepository(new File(repoFolder, ".git")));
    }

    private void start() {
        try {
            server = new Daemon(new InetSocketAddress(port));
            server.getService("git-receive-pack").setEnabled(true);
            resolver = new RepoResolver(repoFolder, bare);
            server.setRepositoryResolver(resolver);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void unpackTemplateProject() {
        new UnZipper().act(templateProjectZip, repoFolder);
    }

    @Override
    public void close() throws Exception {
        server.stop();
        resolver.close();
        git.getRepository().close();
        git.close();
    }

    public Git getGit() {
        return git;
    }
}

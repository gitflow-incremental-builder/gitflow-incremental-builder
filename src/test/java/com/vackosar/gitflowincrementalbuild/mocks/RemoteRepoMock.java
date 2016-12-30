package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.transport.Daemon;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

public class RemoteRepoMock implements AutoCloseable {

    private static int port = 9418;
    private final Git git;
    public String repoUrl = null;
    private static final File DATA_ZIP = new File("src/test/resources/template.zip");
    private static final File REPO_DIR = new File("tmp/remote");
    private boolean bare;
    private Daemon server;
    private RepoResolver resolver;

    public RemoteRepoMock(boolean bare) throws IOException {
        this.bare = bare;
        if (bare) {
            delete(REPO_DIR);
            REPO_DIR.mkdir();
        } else {
            prepareTestingData();
        }
        repoUrl = "git://localhost:" + port + "/repo.git";
        start();
        port++;
        git = new Git(new FileRepository(new File(REPO_DIR + "/.git")));
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            System.out.println("Failed to delete file: " + f + " in LocalRepoMock.delete");
        }
    }

    private void start() {
        try {
            server = new Daemon(new InetSocketAddress(port));
            server.getService("git-receive-pack").setEnabled(true);
            resolver = new RepoResolver(REPO_DIR, bare);
            server.setRepositoryResolver(resolver);
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void prepareTestingData() {
        new UnZiper().act(DATA_ZIP, REPO_DIR);
    }

    @Override
    public void close() throws Exception {
        server.stop();
        resolver.close();
        git.getRepository().close();
        git.close();
        delete(REPO_DIR);
    }

    public Git getGit() {
        return git;
    }
}

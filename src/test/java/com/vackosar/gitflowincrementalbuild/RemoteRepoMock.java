package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.transport.Daemon;

import java.io.File;
import java.net.InetSocketAddress;

public class RemoteRepoMock implements AutoCloseable {

    public static final String REPO_URL = "git://localhost/repo.git";
    private static final File DATA_ZIP = new File("src/test/resources/template.zip");
    private static final File REPO_DIR = new File("tmp/remote");
    private boolean bare;
    private Daemon server;
    private RepoResolver resolver;

    public RemoteRepoMock(boolean bare) {
        this.bare = bare;
        if (bare) {
            try {delete(REPO_DIR);} catch (Exception e) {}
            REPO_DIR.mkdir();
        } else {
            prepareTestingData();
        }
        start();
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new RuntimeException("Failed to delete file: " + f);
        }
    }


    private void start() {
        try {
            server = new Daemon(new InetSocketAddress(9418));
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
        delete(REPO_DIR);
    }
}

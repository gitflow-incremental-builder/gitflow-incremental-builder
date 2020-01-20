package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.io.File;
import java.io.IOException;
import org.eclipse.jgit.transport.Daemon;

class GitProtocolServer implements TestServer {

    private Daemon server;

    @Override
    public String start(File repoFolder) {
        server = new Daemon(TestServerUtils.buildRandomLocalPortAddress());
        server.getService("git-receive-pack").setEnabled(true);
        server.setRepositoryResolver(new RepoResolver<>(repoFolder));
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start JGit daemon for repo at: " + repoFolder, e);
        }
        return TestServerUtils.buildRepoUrl("git", server.getAddress());
    }

    @Override
    public void stop() {
        server.stop();
    }
}

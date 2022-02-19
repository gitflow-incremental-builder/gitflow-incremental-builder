package io.github.gitflowincrementalbuilder.mocks.server;

import java.io.IOException;
import java.net.URI;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Daemon;

class GitProtocolServer implements TestServer {

    private Daemon server;

    @Override
    public URI start(Repository repo) {
        server = new Daemon(TestServerUtils.buildRandomLocalPortAddress());
        server.getService("git-receive-pack").setEnabled(true);
        server.setRepositoryResolver(new SinglePredefinedRepoResolver<>(repo));
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start JGit daemon for repo at: " + repo.getDirectory(), e);
        }
        return TestServerUtils.buildRepoUrl("git", server.getAddress());
    }

    @Override
    public void stop() {
        try {
            server.stop();
        } finally {
            server = null;
        }
    }
}

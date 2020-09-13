package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.net.URI;

import org.eclipse.jgit.lib.Repository;

public interface TestServer {

    URI start(Repository repo);

    void stop();
}

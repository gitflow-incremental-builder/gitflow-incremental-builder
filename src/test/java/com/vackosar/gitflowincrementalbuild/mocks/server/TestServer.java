package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.io.File;

public interface TestServer {

    String start(File repoFolder);

    void stop();
}

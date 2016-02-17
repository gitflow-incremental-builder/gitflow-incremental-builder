package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class RepoTest {

    protected LocalRepoMock localRepoMock;

    @Before
    public void before() throws IOException, URISyntaxException, GitAPIException {
        localRepoMock = new LocalRepoMock();
        System.setProperty("user.dir", LocalRepoMock.WORK_DIR);
    }

    @After
    public void after() throws Exception {
        localRepoMock.close();
    }
}

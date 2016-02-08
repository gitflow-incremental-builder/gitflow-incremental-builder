package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class RepoTest {

    private RepoMock repoMock;

    @Before
    public void before() throws IOException, URISyntaxException, GitAPIException {
        repoMock = new RepoMock();
        System.setProperty("user.dir", RepoMock.WORK_DIR);
    }

    @After
    public void after() throws Exception {
        repoMock.close();
    }
}

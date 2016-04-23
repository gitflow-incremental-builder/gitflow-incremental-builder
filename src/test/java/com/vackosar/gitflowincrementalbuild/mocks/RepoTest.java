package com.vackosar.gitflowincrementalbuild.mocks;

import com.vackosar.gitflowincrementalbuild.boundary.Properties;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class RepoTest {

    protected LocalRepoMock localRepoMock;
    private static final String GIB_UNCOMMITED = "gib.uncommited";

    @Before
    public void before() throws IOException, URISyntaxException, GitAPIException {
        localRepoMock = new LocalRepoMock(false);
        System.setProperty("user.dir", LocalRepoMock.WORK_DIR.toString());
        Properties.REF_BRANCH_PROP.setValue("refs/heads/develop");
        System.setProperty(GIB_UNCOMMITED, Boolean.FALSE.toString());
    }

    @After
    public void after() throws Exception {
        localRepoMock.close();
    }
}

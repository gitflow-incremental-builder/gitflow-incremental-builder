package com.vackosar.gitflowincrementalbuild.mocks;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class RepoTest {

    protected LocalRepoMock localRepoMock;
    public StaticLoggerBinder staticLoggerBinder;

    @Before
    public void before() throws IOException, URISyntaxException, GitAPIException {
        staticLoggerBinder = new StaticLoggerBinder(new ConsoleLoggerManager().getLoggerForComponent("Test"));
        localRepoMock = new LocalRepoMock(false);
        System.setProperty("user.dir", LocalRepoMock.WORK_DIR.toString());
        Property.referenceBranch.setValue("refs/heads/develop");
        Property.uncommited.setValue(Boolean.FALSE.toString());
    }

    @After
    public void after() throws Exception {
        localRepoMock.close();
    }
}

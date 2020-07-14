package com.vackosar.gitflowincrementalbuild.control;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.Test;

import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class DifferentFilesHttpFetchTest extends BaseDifferentFilesTest {

    public DifferentFilesHttpFetchTest() {
        super(TestServerType.HTTP_PROTOCOL);
    }

    @Test
    public void fetch() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), REMOTE_DEVELOP);

        invokeUnderTest();

        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }
}

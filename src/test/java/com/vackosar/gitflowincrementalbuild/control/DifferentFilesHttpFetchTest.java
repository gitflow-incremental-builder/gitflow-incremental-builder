package com.vackosar.gitflowincrementalbuild.control;

import org.assertj.core.api.Assertions;
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

        assertFetchFileCommitExistsInDevelop();
    }

    @Test
    public void fetch_invalidLocalBranch() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), "develop");

        Assertions.assertThatThrownBy(this::invokeUnderTest)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot fetch local reference branch 'develop'");
    }
}

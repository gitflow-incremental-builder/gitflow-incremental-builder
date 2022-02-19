package io.github.gitflowincrementalbuilder.control;

import static io.github.gitflowincrementalbuilder.mocks.server.TestServerType.HTTP_PROTOCOL_BASIC_AUTH;

import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DifferentFilesHttpFetchBasicAuthTest extends BaseDifferentFilesTest {

    public DifferentFilesHttpFetchBasicAuthTest() {
        super(HTTP_PROTOCOL_BASIC_AUTH);
    }

    @BeforeEach
    void configureCredentialHelper() throws IOException {
        StoredConfig config = localRepoMock.getGit().getRepository().getConfig();
        config.setString("credential", null, "helper", "store");
        config.save();
    }

    public void unsetCredentialHelper() throws IOException {
        StoredConfig config = localRepoMock.getGit().getRepository().getConfig();
        config.unset("credential", null, "helper");
        config.save();
    }

    @Test
    public void fetch() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), REMOTE_DEVELOP);
        Files.write(userHome.resolve(".git-credentials"), buildCredentialsFileContent().getBytes());

        invokeUnderTest();

        assertFetchFileCommitExistsInDevelop();
    }

    @SuppressWarnings("resource")
    private String buildCredentialsFileContent() {
        String usrPass = HTTP_PROTOCOL_BASIC_AUTH.getUserName() + ":" + HTTP_PROTOCOL_BASIC_AUTH.getUserSecret() + "@";
        return localRepoMock.getRemoteRepo().repoUri.toString().replace("://", "://" + usrPass);
    }
}

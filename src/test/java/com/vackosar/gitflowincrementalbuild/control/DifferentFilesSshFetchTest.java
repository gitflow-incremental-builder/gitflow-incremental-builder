package com.vackosar.gitflowincrementalbuild.control;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class DifferentFilesSshFetchTest extends BaseDifferentFilesTest {

    private Path sshDir;
    private URI repoUri;

    public DifferentFilesSshFetchTest() {
        super(TestServerType.SSH_PROTOCOL);
    }

    @Override
    @Before
    public void before() throws Exception {
        super.before();

        sshDir = Files.createDirectory(userHome.resolve(".ssh"));
        repoUri = localRepoMock.getRemoteRepo().repoUri;

        String knownHostEntry = String.format("[%s]:%s %s", repoUri.getHost(), repoUri.getPort(), TestServerType.SSH_PROTOCOL.getServerPublicKey());
        Files.write(sshDir.resolve("known_hosts"), Collections.singleton(knownHostEntry));
    }

    @Override
    public void after() throws Exception {
        SshSessionFactory.setInstance(null);    // force reload of known_host etc. (see also org.eclipse.jgit.transport.ssh.SshTestHarness)
        super.after();
    }

    @Test
    public void fetchWithDefaultKeyLocation() throws Exception {
        writePrivateKey("id_rsa", TestServerType.SSH_PROTOCOL.getUserSecret());

        test();
    }

    @Test
    public void fetchWithCustomKeyLocation() throws Exception {
        Path privateKeyPath = writePrivateKey("my_key", TestServerType.SSH_PROTOCOL.getUserSecret());
        // add .ssh/config pointing to custom key
        String sshConfigEntry = String.format("Host %s\n  IdentityFile %s", repoUri.getHost(), privateKeyPath);
        Files.write(sshDir.resolve("config"), Collections.singleton(sshConfigEntry));

        test();
    }

    @Test
    @Ignore // TODO issue-117: introduce @Category(ManualTest.class) and add more info
    public void fetchWithPassphraseEncryptedKey() throws Exception {
        writePrivateKey("id_rsa", TestServerType.SSH_PROTOCOL.getUserSecretEncrypted());

        test();
    }

    private void test() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.fullName(), "true");
        projectProperties.setProperty(Property.referenceBranch.fullName(), REMOTE_DEVELOP);

        invokeUnderTest();

        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }

    private Path writePrivateKey(String fileName, String key) throws IOException {
        return Files.write(sshDir.resolve(fileName), Collections.singleton(key));
    }
}

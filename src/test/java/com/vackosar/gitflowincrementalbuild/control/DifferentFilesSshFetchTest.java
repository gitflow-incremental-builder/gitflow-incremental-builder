package com.vackosar.gitflowincrementalbuild.control;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.ProcessUtils;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class DifferentFilesSshFetchTest extends BaseDifferentFilesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DifferentFilesSshFetchTest.class);

    private Path sshDir;
    private URI repoUri;

    public DifferentFilesSshFetchTest() {
        super(TestServerType.SSH_PROTOCOL);
    }

    // need to override before() so that setup is skipped if @RunOnlyWhen does not apply
    @Override
    @BeforeEach
    protected void before(final TestInfo testInfo) throws Exception {
        // check @RunOnlyWhen
        testInfo.getTestMethod().map(meth -> meth.getAnnotation(RunOnlyWhen.class)).map(RunOnlyWhen::value).ifPresent(
                condition -> Assumptions.assumeTrue(condition.present(), testInfo.getDisplayName() + "() is only supported when running " + condition));

        super.before(testInfo);
        
        // see GSSUtil.DEFAULT_HANDLER. Setting this to a non-empty String prevents ConsoleCallbackHandler from being used - even without a valid class name
        java.security.Security.setProperty("auth.login.defaultCallbackHandler", "simplefix");

        sshDir = Files.createDirectory(userHome.resolve(".ssh"));
        repoUri = localRepoMock.getRemoteRepo().repoUri;

        String knownHostEntry = String.format("[%s]:%s %s", repoUri.getHost(), repoUri.getPort(), TestServerType.SSH_PROTOCOL.getServerPublicKey());
        Files.write(sshDir.resolve("known_hosts"), Collections.singleton(knownHostEntry));
    }

    @AfterEach
    void resetSshSessionFactory() throws Exception {
        SshSessionFactory.setInstance(null);    // force reload of known_host etc. (see also org.eclipse.jgit.transport.ssh.SshTestHarness)
    }

    @Test
    public void fetchWithDefaultKeyLocation() throws Exception {
        projectProperties.put(Property.useJschAgentProxy.name(), "false");
        writePrivateKey("id_rsa", TestServerType.SSH_PROTOCOL.getUserSecret());

        test();
    }

    @Test
    public void fetchWithCustomKeyLocation() throws Exception {
        projectProperties.put(Property.useJschAgentProxy.name(), "false");
        Path privateKeyPath = writePrivateKey("my_key", TestServerType.SSH_PROTOCOL.getUserSecret());
        // add .ssh/config pointing to custom key
        String sshConfigEntry = String.format("Host %s\n  IdentityFile %s", repoUri.getHost(), privateKeyPath);
        Files.write(sshDir.resolve("config"), Collections.singleton(sshConfigEntry));

        test();
    }

    @Test
    @RunOnlyWhen(RunCondition.ON_TRAVIS_OR_FORCED)    // "pollutes" ssh-agent, default execution is only safe on Travis-CI
    public void fetchWithPassphraseEncryptedKey() throws Exception {
        projectProperties.put(Property.useJschAgentProxy.name(), "true");
        writePrivateKey("id_rsa", TestServerType.SSH_PROTOCOL.getUserSecretEncrypted());
        Path unencryptedPrivateKeyPath = writePrivateKey("id_rsa_unencrypted", TestServerType.SSH_PROTOCOL.getUserSecret());
        ProcessUtils.startAndWaitForProcess("ssh-add", unencryptedPrivateKeyPath.toAbsolutePath().toString());
        Files.delete(unencryptedPrivateKeyPath);

        test();
    }

    @Test
    @RunOnlyWhen(RunCondition.ON_WINDOWS_FORCED)    // expects putty on PATH, requires user interaction and "pollutes" pageant
    public void fetchWithPassphraseEncryptedKey_manualWindowsTest() throws Exception {
        projectProperties.put(Property.useJschAgentProxy.name(), "true");
        writePrivateKey("id_rsa", TestServerType.SSH_PROTOCOL.getUserSecretEncrypted());
        Path unencryptedPrivateKeyPath = writePrivateKey("id_rsa_unencrypted", TestServerType.SSH_PROTOCOL.getUserSecret()).toAbsolutePath();
        Path ppkPath = unencryptedPrivateKeyPath.resolveSibling("key.ppk").toAbsolutePath();

        LOGGER.info("*** USER ACTION REQUIRED! *** Launching puttygen. Save ppk as (and close puttygen!): " + ppkPath);
        ProcessUtils.startAndWaitForProcess("puttygen", unencryptedPrivateKeyPath.toString());
        Files.delete(unencryptedPrivateKeyPath);

        LOGGER.info("Launching pageant in background.");
        ProcessUtils.startAndWaitForProcess("start", "pageant", ppkPath.toString());
        // no need to delete ppk (which might also cause a failure due to concurrent pageant access)

        test();
    }

    private void test() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), REMOTE_DEVELOP);

        invokeUnderTest();

        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }

    private Path writePrivateKey(String fileName, String key) throws IOException {
        Path privateKey = Files.write(sshDir.resolve(fileName), Collections.singleton(key));
        return SystemUtils.IS_OS_WINDOWS
                ? privateKey
                : Files.setPosixFilePermissions(privateKey, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    private @interface RunOnlyWhen {

        RunCondition value();
    }

    enum RunCondition {
        ON_TRAVIS_OR_FORCED {
            @Override
            boolean present() {
                // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
                return System.getenv().containsKey("TRAVIS") || Boolean.getBoolean(name());
            }

            @Override
            public String toString() {
                return "on Travis-CI or with -D" + name() + "=true";
            }
        },
        ON_WINDOWS_FORCED {
            @Override
            boolean present() {
                return SystemUtils.IS_OS_WINDOWS && Boolean.getBoolean(name());
            }

            @Override
            public String toString() {
                return "on Windows with -D" + name() + "=true";
            }
        };

        abstract boolean present();
    }
}

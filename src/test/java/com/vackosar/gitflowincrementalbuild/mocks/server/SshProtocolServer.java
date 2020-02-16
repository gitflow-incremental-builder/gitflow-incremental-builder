package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.eclipse.jgit.junit.ssh.SshTestGitServer;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshProtocolServer implements TestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SshProtocolServer.class);

    static final String USER_NAME = "foo";

    static final String USER_KEY_PRIVATE =
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEowIBAAKCAQEArrj2oR3Ak7npWLVzazEq0u29nofvuXWmakSSMb2pN372S8m4\n" +
            "GHoITJKmusK0xFI2qgAxz/4CCEF8G9Kr+fEHFiNbV8PF0aeLXA9T2zcuNoavP9Gp\n" +
            "7PsfrCpD8Vloqg4t3qa76yBT511vR90xwnazfIr+fp7ODs6xfI0OirXSwtKOk4p/\n" +
            "ntWwj0Itaja2X2d9j9faQ7WQs+XNZf3tSwmnUsC3n4wnzzamD51MiL4R0SCZEiPN\n" +
            "IiuGQBWUea8cI6beZ3bWXcxwFeHDsyXvI5IEJuBDcrbRK0xVESiYm/nz0gvehbLT\n" +
            "GnXxKNkZIErPqmyAyFgpuBD/2QwbszKa3fRcrwIDAQABAoIBAHgVI1wkSKC/G4Me\n" +
            "Yk7/ocEKKFJ2dunt1AwhSKDrCwvbNIduAhrAdEe8Wt7sxrRPFMbOD24100u9RgER\n" +
            "T6UeZJJwhmzRXnnzPrJszmNRj29mLbgc7z6ycVgwTDXqDychS6FE0s6Mj13qN3sa\n" +
            "qQJP9pfYJ4T6vAAtSoqhn6bxxAKk87+X2HI3CcsJNnbDovFOVPaxS9kqfge9JSUX\n" +
            "cl7vSbRLzIyUGz5tf5EOxBSK/EA2Mj+aP5Wn8N7SdvKKKDx5trXn7wgaxw2pY2QZ\n" +
            "Nfe/qUp/XwEMG6ztWOzxDjTkw8TW/qwEotvlbf8DCE/aggit91JLrb26JTPl/BiZ\n" +
            "DyMHY/ECgYEA6U85NCECxEd3nkzU9qR/qQOGXzeRfoR6VVLEVw6+UI4+i7MDN9vk\n" +
            "BzS7wM/JyGzbSrieVA16KKfxP81Oa4h6GJugv3BqHoUExf0StInUL22C8UzcRgw4\n" +
            "JMsiRx2kLPmwL940YdNA2+hYKkF/JEg53R4eSeAB0y0R9+jO3+M380sCgYEAv7cV\n" +
            "MkpG2GYaDXAw6tFTZXCJEwvsSRlFFqVP+7VEAOQZxUyMtlPHwlzllgZqWlsm1uSs\n" +
            "6Qlfs2xnoxb5BFJeU6Y3+nDha3JIdi7zPcck5CMWONqBekIMwVnfMoeVGXbHLEdJ\n" +
            "QCKGG9qRgqCIbu0dsIrepxRogPhv8vzuzD7j+a0CgYAVipYKhR2/R6X4vLlRCIEs\n" +
            "9sFaW0QYvVyaMikkrJzPzUJjHaUnbCsSq0DGnajQ05QvwvoDYrcrt83jiS47aX90\n" +
            "dDIUWunBZaC6MxKeDrfDpUXYx7Ly2L/6TbMdg9QbvvYQhWCqw5mSdFJnnGKD1BNb\n" +
            "oiNDyOYCPIATNrCbJVyOHwKBgQChd7Sdh5uFlPDqUoyQqT7BF/gLF6appmPe/9qO\n" +
            "mAuvaG4gEyoMQiHjnFQteDfI5C6hHTZYi3GT74CWBroynqEdeMh64OmqkjVffImX\n" +
            "hYuXrcmAluAoNUsC6gunRjQYn0Z/D4ctQiaLfRnC2CwwqPqxfzSpqfGedh/rmoYR\n" +
            "dgKSfQKBgC8HXWN6XKFb4GODjVjdpGOI3aIz5X2UfqL7HKQNuMkNVk0fuTiGKZ4m\n" +
            "Ilid053O84lyvgy37+nqfGlXzSfRBQ6LGf6D5+w1RP+dzRSlWh3N1Bb0dO+plnY+\n" +
            "dEdsqsHKdJb8ugkirjSHsegNI3/CR9Xgex0t1BSSMYpS98kGcDXE\n" +
            "-----END RSA PRIVATE KEY-----\n";

    static final String USER_KEY_PUBLIC =
            "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCuuPahHcCTuelYtXNrMSrS7b2eh++5daZq" +
            "RJIxvak3fvZLybgYeghMkqa6wrTEUjaqADHP/gIIQXwb0qv58QcWI1tXw8XRp4tc" +
            "D1PbNy42hq8/0ans+x+sKkPxWWiqDi3eprvrIFPnXW9H3THCdrN8iv5+ns4OzrF8" +
            "jQ6KtdLC0o6Tin+e1bCPQi1qNrZfZ32P19pDtZCz5c1l/e1LCadSwLefjCfPNqYP" +
            "nUyIvhHRIJkSI80iK4ZAFZR5rxwjpt5ndtZdzHAV4cOzJe8jkgQm4ENyttErTFUR" +
            "KJib+fPSC96FstMadfEo2RkgSs+qbIDIWCm4EP/ZDBuzMprd9Fyv foo";

    static final String SERVER_KEY_PUBLIC =
            "ssh-rsa AAAAB3NzaC1yc2EAAAABJQAAAQEAzeJw/xy/ecnRuPvCPIqZma4F7ai2pAaXfpwr" +
            "pp0aclzcm836OAYYcllncCI9HnOk4aDt2TtWfOeX6+2uNWYdk7pBBIg9BtCWdd8a" +
            "1jruLPhGPkio2eZuieVtK9VXU/krz2uwOY5TbhytzFZL9rSEB97eZGPquTbJCxTb" +
            "OpZI8eR8oWs00mg0O7pbsqVD9DNP3wzAzb4XUS2stlM9BNWUP3h/J4qV9dSI7OAq" +
            "YXQDfuF1pJC6Jz0E5YoMBB3FZIjrS8zAWNwBJSC9rcNjGxN5NJrRGjT5/Bk7vDk8" +
            "Y6oIkZGU9nO47LfbJiDiIfL6ErK4Wdbr+dvZxkJSDizy/mCQdw==";

    private static final String SERVER_KEY_PRIVATE =
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEoQIBAAKCAQEAzeJw/xy/ecnRuPvCPIqZma4F7ai2pAaXfpwrpp0aclzcm836\n" +
            "OAYYcllncCI9HnOk4aDt2TtWfOeX6+2uNWYdk7pBBIg9BtCWdd8a1jruLPhGPkio\n" +
            "2eZuieVtK9VXU/krz2uwOY5TbhytzFZL9rSEB97eZGPquTbJCxTbOpZI8eR8oWs0\n" +
            "0mg0O7pbsqVD9DNP3wzAzb4XUS2stlM9BNWUP3h/J4qV9dSI7OAqYXQDfuF1pJC6\n" +
            "Jz0E5YoMBB3FZIjrS8zAWNwBJSC9rcNjGxN5NJrRGjT5/Bk7vDk8Y6oIkZGU9nO4\n" +
            "7LfbJiDiIfL6ErK4Wdbr+dvZxkJSDizy/mCQdwIBJQKCAQAWQf5gxNZuB/r/Pc/P\n" +
            "MZQQnTESxiGVMSUicb+HokgMXRDuP8gGDn8u9OiWgD31dEktJieNGyvx09kSljxY\n" +
            "zMTtYD5hWtYqQBBEGB6Ts1gE3JH45UKvxeJvxcac9HgkwP3RPBMNJCSx9UM4r2Io\n" +
            "gjfJf+Cw6DUNGq3zXDNgR5ku6BMa7HfpaD0THe4GUpwdJV49xyHEzuzbhjUxo8kf\n" +
            "51zH27xmwZ5lK9ffpy4KywK38GInQvpA8FI7273gPA5u+O4zuMQwgyyqx7ASqk2N\n" +
            "LtfVi4d9ppRgoSu+Hx3/3anPHxY4LBEyEXd3KsDaWLxP+4amsbp6MoC7Ihekcrje\n" +
            "LX6tAoGBAPMU3GPfDCG8aTMzf5DoX5YilQOHWh/4u2xtgtZYnz2aTg/tJ+tsW8b8\n" +
            "aTvNwoSCIYD/g+pR28j4uyHqMVnMy2JUMZ8KdPjGRJgELVLf/S/n0lAYq0k/exOl\n" +
            "leVo70Aonb9qBEMw9zfaybOIAnMmDYAjv+MwJYUC8oOOyyUlTLmhAoGBANjTg3vE\n" +
            "V92OfE7EqY7+OhIBBcTp6ehp2NQ9pWJFGjxseeCD47FhozUbrISv26XXUGi54UT8\n" +
            "skNDHk2/wOYVPu2ZLgqNqaszJQj87sLhJPzVfdu3RNQ+y1N4A4F7+GWtFEgE19as\n" +
            "nxI9C7a2O7kIzpqwKwvfYy4AVHPOPJ/R7wMXAoGBAOXxI4ERQtPN5vkVCffHDk+/\n" +
            "2REtAjnrSYI+E/sxPK/k/bwJ2adYqdfn2Sq7uADOH7FgaAcq1tLdcr83esqRSsTM\n" +
            "LvBj1m7JY3sK3sQEUF2/nW5chloLne/9f/SvXtvTZMnS/Rz297FgQkILCTx98RhZ\n" +
            "KyMEB9DZRkUYX0ymvjMNAoGAC7hn+NouQ1PPXjQk10wDI5FaQf7OX5cEzTPDwB9p\n" +
            "M7LWJ8/HHlhOAt7muxBsvNs0x2P+VsGNGGR+LbdWiPo6wLwsAJIJK9lARazOosAP\n" +
            "1lC6se47EmRCV4nyWgazex4cxZ9lnOa8fYbCXOdBf5+cdxBxB4+I0g3bDS2+FnoM\n" +
            "62kCgYA22gJJ6Jn5+oZ0VqB7h3a8elLBFPkra3x7giKtykmxu3AGzgpY3XtRPrL+\n" +
            "JAe9qfsF6EFaB523CBrvJyZ+ZT8pxt9N43lRGBDG5r/9kpsvoyVGXzrSoTETiALY\n" +
            "DF+Ratt5J2SG6lUkayYntd9/M+oG7vZjPUidPu+isdgwG1mQCw==\n" +
            "-----END RSA PRIVATE KEY-----";

    private SshTestGitServer sshTestGitServer;

    @Override
    public URI start(Repository repo) {
        try {
            sshTestGitServer = new SshTestGitServer(USER_NAME, writeUserPublicKey(), repo, SERVER_KEY_PRIVATE.getBytes(StandardCharsets.US_ASCII));
            int port = sshTestGitServer.start();
            return TestServerUtils.buildRepoUrl("ssh", USER_NAME, port);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to start SshTestGitServer for repo at: " + repo.getDirectory(), e);
        }
    }

    private Path writeUserPublicKey() throws IOException {
        Path tempKeyFile = Files.createTempFile(getClass().getName(), null);
        tempKeyFile.toFile().deleteOnExit();
        Files.write(tempKeyFile, Collections.singleton(USER_KEY_PUBLIC));
        return tempKeyFile;
    }

    @Override
    public void stop() {
        if (sshTestGitServer != null) {
            try {
                sshTestGitServer.stop();
            } catch (IOException e) {
                LOGGER.warn("Failed to stop SshTestGitServer", e);
            } finally {
                sshTestGitServer = null;
            }
        }
    }
}

package io.github.gitflowincrementalbuilder.mocks.server;

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
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEogIBAAKCAQEAhuM4WyNt2LcevyP+ljuJq8fQb/mZDmBDpkE6XCLiDd3fYgQ3
            kFMs9/QDJCtpX4dsuQDyCceo4oba9y0Uys550t+ypOujcXy+pE0O0ZvR1dAOU4vV
            94UEZayDOM7DQaHOn+EzZujLoOz745S89OlZGiotIcbZH4jdUyUxBAwhFLPNHEJe
            kB0pF9D3Usjg0cz5w2u26IVXzHATdcqAMx9GmysNZ858STia/zRT3HLkzddknwzw
            A6pLkfia8kSy/g+KJj8O9FDafnPoPcTkEjdAerB5xUCIQAqrqa/Z3sVjzoyW/mrz
            OhfTIBFZdJmZn77G7UM+nJh+X5ymK+M82ZizFwIDAQABAoIBAAJziKenWiyxDz78
            AXlrdZIInJIcYBqxxyjzUIMyIxeLa67VUsktqciLx67VlyGkTWaDTOK+nSQYvUv2
            psUUwYQCirWSjrLWSISl3doTUdnn4QrFpMrNwQmz++KBZ2z+6slfB1ccbe7rv5wg
            LNrqjrL6Bz3w5is7ioIjT+O58LP2lceJAcDphU+CGLPoXrr7OBYJRyaSKyEhEclD
            ZDzvtr+LU8tG16Cl8S4hzQWnuVZrwb4SXdxq49ZdVfUiLNU9iToqbmHzh9REbPuA
            O8GzQJP4xZ5cV9eBWmMxfTPBScPDMPEoYaBAUdXhwMYYe2n3332G9G+mneA7QlNa
            3Nxin4ECgYEAu4p0dJwhxonlbp/yb03dVX7MOKDh4EinERM4+yuIAO4UgONydtCD
            2EBb9oxR8JkP035fnxwLJYVSQSkDLwudMcSZPQmracl+1fDwOUtS9USAemnPzTkU
            xGhZwq+iKAPKPtt9MpyhJ6+HTXneixak7O49yOtf+eIkb6hafZ0gbsECgYEAuCBb
            7kT1Goqbvm8dPP5AO1HfE7MK64V2RgV1eA/77zZsBkLhF9n0NN2WINC4ffR9ly7X
            B6efGAXzBFoZwfoa+hWhZziwqiQHgMw/OYPTruXwCCQ1Jaw2MfBUEjAbYkGiKn42
            BUL7s8d3NKL6Wyyc3qOS5tacI5UVpfBBjb7Wb9cCgYBhT8h5sYI9GNR6AHi1lHui
            nzass55A2LIHxCeu/LmHcgIllt+QE0Y2cb7GQa1K4ME7hrlrQAvwnis+MF+8i5Q3
            fMHe0COnsqwjqu+bayBSsAbSfhEbdeD2wQbCZIwJo13QG+fs2SUuCIB2jSQSm99c
            KYrZtHiKmvM5FOxPfbaUgQKBgDp4rDkCVQPUuJjFGHfiFevAmCLdXL5mZ6Tc3uz9
            xne0xKFIY8r7P/350E8jeTMmjSuRiF+571/lo8LiCgP7tM1uSQ9KhW/CeU+BjSJq
            prKH+q3bMbWA9sTtGQWdmVSemyz7X5RULTJuSYDBsNd7V2WsdF4yEOuL0JAdt/OX
            gumJAoGAeMx63gkm0h43ua40Qtdy2b7AoCVR8HZ118HTTvC/sq/3r/rXZ6JdeWv2
            IZVLA8wOLnWA0zd8wZpbYdzzmsgm5wRfrxNPXaOEQx+36Utxo4sQzFdUCjFL72LB
            tyIXZV8hlOXoHAAXMU0AxeERKIh65/1/FnnG1glah5kmRY0e0MI=
            -----END RSA PRIVATE KEY-----""";

    /**
     * Encrypted variant of {@link #USER_KEY_PRIVATE}, password is "changeit".
     */
    static final String USER_KEY_PRIVATE_ENC =
            """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: DES-EDE3-CBC,41CB4B152855CBE1
            
            64dk8t6J55hIP48MccNLujJJyzYWMI4TGCYtX2rL9H0vyl/Bq5IbRBd/pOMugR7q
            bJXm9UpWq1HOo6afE0YNq+a2/RuZIQvhenbTqaTfwOiFj9vgidnKzx1pu3rpVKR9
            5UHuzVZi6W43hvwv5UAV16FnCfk5uOBq8DDRYl5S5qgswQDKPegLA5hzv0ceIASp
            s5ArK1BFf4wJ4DDsNVIr6xuH2cxMEAzcsycczQX/c4JTbjutm/ZIdVkz0RJr0FBh
            WWtWVlYJshJemhAERkbTryhbkWjwm2eC83xlMXkLrlvHM5Uw3IPcQTFgs3BKAqqr
            UvoKjpBdPsw7rPTyoxGJ/co2EeDRNsfRgTSho12qfU2rhfGUETnvHps/Pb8T1MQ1
            L9o4q3xeSW4hxA39inhWEcq6+UyQMUH+jJYduIJpThiYYDD6rKDZdBeE9D9dPc1W
            bByxLd4Qkx8vTnk44Ry+3NR9o2aduqjxfrKyEEfK7FaaDBck0iwoq8IY32s+ZJIP
            n+26kNoDa3oedmxlWcgFgNXTWJbB+jcu0NI6eyimMdmHSrcgleMgl9G+/zvmqc0G
            TExYaTh72W/Yay/CIVRFWFRPqd9S5vt6P+Bk7aH1P/vF0Wn1l5kJuhk1l4q2Ut4e
            Z+M2hjSY4+J350lS9lsLIjlW8BoKkkVvtXhAKyKpNThNucLqx0A1E3cHexOfPLzm
            mHfSgkKi/iM6+XJLst6gIEFFuavQs4DORl3ZpaEez4+zMcwmC9+gFqBjUQ4W/ZQg
            C6uJJgaEgpSkl1ESN8vu7QHGRZNeYQSwxAy3QpPd1HGdAmcrGCJiicdru1N4Ez8J
            2tGgiOfwxaZaf0jIumWbl1HjJD/2FiLMZFIq3u+0hOLNqLEfjuJYfarBAWK+jMYf
            jT51aDKCmN2ivMKyIgPEWoLICa2uwizOkj4OZ69flBm9ZNpNVGHEccjM7l9dM8nt
            2lAZwbWLGY00UPPn8Uqg6KHGTCiV6S+H4pxHkLAQ/xfx8ssOY1/yTNpyP/6sD4A2
            bCygQv9OxB5mtFzmLfD1RmNlEnOQ7JpF3RG2X1oPqVZhriUko4IGnGI8F94yYZH4
            koO8z1ktVpXKaa2Km+74UI4P8CiEdB5DSGYq2mdjLTH5WDQkUTaKSEjTEyKQU8YB
            14zfOGnkUCBSz5fXuN8dm3OBEbiEwWtIMGzfrZz3XKIvyFpkQXyvcv9Qt4mz1Dz8
            JA4s5cBe2dMXCRJZuJcwSYctNwS8/CoVCzrgnJBzEaF5BVB9CE9zaZXXH7B4PcHR
            2GWfOjn92Y4JKE6jXCK1HKS7F6BgeglBnmgkDhanMZUwc+lDY9y4cE1Mugfe2oxC
            M3qBmn5IZMHt1XPbcF6ZyIQo5lcNHxWz12sau6c4+kwtdk4Qb0o+7pxagMLo/tZ6
            lu51AkrbosbR3AsKA497vfbzsXOopWAbXVQcVrcPTnMv9zGNuXy3uCnCPEESqIKj
            XYL168leLgu2Bu1iXUsxCGy+sWaSl1NgmvdHndeKT53SCTyWBtSQmMvy/fmXAb5D
            2CwcTtXLDkoUKwnklaMberUW63WcdbyLMhLoPtPT2PKjUut6mHaYZw==
            -----END RSA PRIVATE KEY-----""";

    static final String USER_KEY_PUBLIC =
            "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCG4zhbI23Ytx6/I/6WO4mrx9Bv" +
            "+ZkOYEOmQTpcIuIN3d9iBDeQUyz39AMkK2lfh2y5APIJx6jihtr3LRTKznnS37Kk" +
            "66NxfL6kTQ7Rm9HV0A5Ti9X3hQRlrIM4zsNBoc6f4TNm6Mug7PvjlLz06VkaKi0h" +
            "xtkfiN1TJTEEDCEUs80cQl6QHSkX0PdSyODRzPnDa7bohVfMcBN1yoAzH0abKw1n" +
            "znxJOJr/NFPccuTN12SfDPADqkuR+JryRLL+D4omPw70UNp+c+g9xOQSN0B6sHnF" +
            "QIhACqupr9nexWPOjJb+avM6F9MgEVl0mZmfvsbtQz6cmH5fnKYr4zzZmLMX foo";

    static final String SERVER_KEY_PUBLIC =
            "ssh-rsa AAAAB3NzaC1yc2EAAAABJQAAAQEAzeJw/xy/ecnRuPvCPIqZma4F7ai2" +
            "pAaXfpwrpp0aclzcm836OAYYcllncCI9HnOk4aDt2TtWfOeX6+2uNWYdk7pBBIg9" +
            "BtCWdd8a1jruLPhGPkio2eZuieVtK9VXU/krz2uwOY5TbhytzFZL9rSEB97eZGPq" +
            "uTbJCxTbOpZI8eR8oWs00mg0O7pbsqVD9DNP3wzAzb4XUS2stlM9BNWUP3h/J4qV" +
            "9dSI7OAqYXQDfuF1pJC6Jz0E5YoMBB3FZIjrS8zAWNwBJSC9rcNjGxN5NJrRGjT5" +
            "/Bk7vDk8Y6oIkZGU9nO47LfbJiDiIfL6ErK4Wdbr+dvZxkJSDizy/mCQdw==";

    private static final String SERVER_KEY_PRIVATE =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEoQIBAAKCAQEAzeJw/xy/ecnRuPvCPIqZma4F7ai2pAaXfpwrpp0aclzcm836
            OAYYcllncCI9HnOk4aDt2TtWfOeX6+2uNWYdk7pBBIg9BtCWdd8a1jruLPhGPkio
            2eZuieVtK9VXU/krz2uwOY5TbhytzFZL9rSEB97eZGPquTbJCxTbOpZI8eR8oWs0
            0mg0O7pbsqVD9DNP3wzAzb4XUS2stlM9BNWUP3h/J4qV9dSI7OAqYXQDfuF1pJC6
            Jz0E5YoMBB3FZIjrS8zAWNwBJSC9rcNjGxN5NJrRGjT5/Bk7vDk8Y6oIkZGU9nO4
            7LfbJiDiIfL6ErK4Wdbr+dvZxkJSDizy/mCQdwIBJQKCAQAWQf5gxNZuB/r/Pc/P
            MZQQnTESxiGVMSUicb+HokgMXRDuP8gGDn8u9OiWgD31dEktJieNGyvx09kSljxY
            zMTtYD5hWtYqQBBEGB6Ts1gE3JH45UKvxeJvxcac9HgkwP3RPBMNJCSx9UM4r2Io
            gjfJf+Cw6DUNGq3zXDNgR5ku6BMa7HfpaD0THe4GUpwdJV49xyHEzuzbhjUxo8kf
            51zH27xmwZ5lK9ffpy4KywK38GInQvpA8FI7273gPA5u+O4zuMQwgyyqx7ASqk2N
            LtfVi4d9ppRgoSu+Hx3/3anPHxY4LBEyEXd3KsDaWLxP+4amsbp6MoC7Ihekcrje
            LX6tAoGBAPMU3GPfDCG8aTMzf5DoX5YilQOHWh/4u2xtgtZYnz2aTg/tJ+tsW8b8
            aTvNwoSCIYD/g+pR28j4uyHqMVnMy2JUMZ8KdPjGRJgELVLf/S/n0lAYq0k/exOl
            leVo70Aonb9qBEMw9zfaybOIAnMmDYAjv+MwJYUC8oOOyyUlTLmhAoGBANjTg3vE
            V92OfE7EqY7+OhIBBcTp6ehp2NQ9pWJFGjxseeCD47FhozUbrISv26XXUGi54UT8
            skNDHk2/wOYVPu2ZLgqNqaszJQj87sLhJPzVfdu3RNQ+y1N4A4F7+GWtFEgE19as
            nxI9C7a2O7kIzpqwKwvfYy4AVHPOPJ/R7wMXAoGBAOXxI4ERQtPN5vkVCffHDk+/
            2REtAjnrSYI+E/sxPK/k/bwJ2adYqdfn2Sq7uADOH7FgaAcq1tLdcr83esqRSsTM
            LvBj1m7JY3sK3sQEUF2/nW5chloLne/9f/SvXtvTZMnS/Rz297FgQkILCTx98RhZ
            KyMEB9DZRkUYX0ymvjMNAoGAC7hn+NouQ1PPXjQk10wDI5FaQf7OX5cEzTPDwB9p
            M7LWJ8/HHlhOAt7muxBsvNs0x2P+VsGNGGR+LbdWiPo6wLwsAJIJK9lARazOosAP
            1lC6se47EmRCV4nyWgazex4cxZ9lnOa8fYbCXOdBf5+cdxBxB4+I0g3bDS2+FnoM
            62kCgYA22gJJ6Jn5+oZ0VqB7h3a8elLBFPkra3x7giKtykmxu3AGzgpY3XtRPrL+
            JAe9qfsF6EFaB523CBrvJyZ+ZT8pxt9N43lRGBDG5r/9kpsvoyVGXzrSoTETiALY
            DF+Ratt5J2SG6lUkayYntd9/M+oG7vZjPUidPu+isdgwG1mQCw==
            -----END RSA PRIVATE KEY-----""";

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

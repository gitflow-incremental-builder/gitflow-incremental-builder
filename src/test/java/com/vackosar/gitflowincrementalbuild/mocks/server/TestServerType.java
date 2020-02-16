package com.vackosar.gitflowincrementalbuild.mocks.server;

public enum TestServerType {

    GIT_PROTOCOL {
        @Override
        public TestServer buildServer() {
            return new GitProtocolServer();
        }
    },
    HTTP_PROTOCOL {
        @Override
        public TestServer buildServer() {
            return new HttpProtocolServer();
        }
    },
    HTTP_PROTOCOL_BASIC_AUTH {
        @Override
        public TestServer buildServer() {
            return new HttpProtocolServer(getUserName(), getUserSecret());
        }

        @Override
        public String getUserName() {
            return "foo";
        }

        @Override
        public String getUserSecret() {
            return "bar";
        }
    },
    SSH_PROTOCOL {

        @Override
        public TestServer buildServer() {
            return new SshProtocolServer();
        }

        @Override
        public String getUserName() {
            return SshProtocolServer.USER_NAME;
        }

        @Override
        public String getUserSecret() {
            return SshProtocolServer.USER_KEY_PRIVATE;
        }

        @Override
        public String getServerPublicKey() {
            return SshProtocolServer.SERVER_KEY_PUBLIC;
        }
    };

    public abstract TestServer buildServer();

    public String getUserName() {
        return null;
    }

    /**
     * @return the (optional) user secret (password or private key)
     */
    public String getUserSecret() {
        return null;
    }

    public String getServerPublicKey() {
        return null;
    }
}

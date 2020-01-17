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
            return new HttpProtocolServer(getUsername(), getPassword());
        }

        @Override
        public String getUsername() {
            return "foo";
        }

        @Override
        public String getPassword() {
            return "bar";
        }
    };

    public abstract TestServer buildServer();

    public String getUsername() {
        return null;
    }

    public String getPassword() {
        return null;
    }
}

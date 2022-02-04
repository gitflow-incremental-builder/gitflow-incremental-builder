package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

public class TestServerUtils {

    static InetSocketAddress buildRandomLocalPortAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    static URI buildRepoUrl(String protocol, InetSocketAddress address) {
        return toURI(String.format("%s://%s:%s/repo.git", protocol, address.getHostName(), address.getPort()));
    }

    static URI buildRepoUrl(String protocol, InetSocketAddress address, int port) {
        return toURI(String.format("%s://%s:%s/repo.git", protocol, address.getHostName(), port));
    }

    static URI buildRepoUrl(String protocol, String user, int port) {
        return toURI(String.format("%s://%s@localhost:%s/repo.git", protocol, user, port));
    }

    private static URI toURI(String spec) {
        try {
            return new URI(spec);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to create URL for: " + spec, e);
        }
    }
}

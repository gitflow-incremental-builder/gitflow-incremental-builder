package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class TestServerUtils {

    static InetSocketAddress buildRandomLocalPortAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    static String buildRepoUrl(String protocol, InetSocketAddress address) {
        return String.format("%s://%s:%s/repo.git", protocol, address.getHostName(), address.getPort());
    }

    static String buildRepoUrl(String protocol, InetSocketAddress address, int port) {
        return String.format("%s://%s:%s/repo.git", protocol, address.getHostName(), port);
    }
}

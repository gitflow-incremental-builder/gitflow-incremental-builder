package com.vackosar.gitflowincrementalbuild.mocks.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HttpProtocolServer implements TestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProtocolServer.class);

    private static final String[] ROLES = new String[] { "can-access" };

    private final String username;
    private final String password;

    private Server server;

    public HttpProtocolServer() {
        this(null, null);
    }

    public HttpProtocolServer(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public URI start(Repository repo) {
        InetSocketAddress address = TestServerUtils.buildRandomLocalPortAddress();

        server = new Server(address);
        configureServer(server, repo);

        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Jetty servlet container for repo at: " + repo.getDirectory(), e);
        }
        return TestServerUtils.buildRepoUrl("http", address, server.getURI().getPort());
    }

    private void configureServer(Server server, Repository repo) {

        server.setHandler(buildServletHandler(repo));
        
        if (username != null) {
            addBasicAuth(server);
        }
    }

    private ServletHandler buildServletHandler(Repository repo) {
        GitServlet gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(new SinglePredefinedRepoResolver<>(repo));

        ServletHolder holder = new ServletHolder(gitServlet);
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(holder, "/*");
        return servletHandler;
    }

    // https://www.eclipse.org/jetty/documentation/current/configuring-security.html#_authentication_and_authorization_with_embedded_jetty
    private void addBasicAuth(Server server) {
        
        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.setAuthenticator(new BasicAuthenticator());

        Constraint constraint = new Constraint();
        constraint.setAuthenticate(true);
        constraint.setRoles(ROLES);
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);
        security.setConstraintMappings(Collections.singletonList(mapping));

        HashLoginService loginService = new HashLoginService();
        loginService.setUserStore(buildUserStore());
        server.addBean(loginService);
        security.setLoginService(loginService);

        security.setHandler(server.getHandler());
        server.setHandler(security);
    }

    private UserStore buildUserStore() {
        UserStore userStore = new UserStore();
        userStore.addUser(username, new Password(password), ROLES);
        return userStore;
    }

    @Override
    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            LOGGER.warn("Failed to stop Jetty servlet container", e);
        } finally {
            server = null;
        }
    }

}

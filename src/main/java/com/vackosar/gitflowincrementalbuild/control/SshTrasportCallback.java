package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.vackosar.gitflowincrementalbuild.boundary.Arguments;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class SshTrasportCallback implements TransportConfigCallback {

    private final Optional<Path> key;

    @Inject
    public SshTrasportCallback(Arguments arguments) {
        this.key = arguments.key;
    }

    private JschConfigSessionFactory getFactory(Path key) {
        return new JschConfigSessionFactory() {

            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                final JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");
                defaultJSch.addIdentity(key.toString());
                return defaultJSch;
            }
        };
    }

    @Override
    public void configure(Transport transport) {
        if (key.isPresent() && transport instanceof SshTransport) {
            ((SshTransport) transport).setSshSessionFactory(getFactory(key.get()));
        }
    }
}

package com.vackosar.gitflowincrementalbuild.control.jgit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;

/**
 * Sets to each created {@link Session} a {@link IdentityRepository} that contains (unencrypted) {@link Identity Identities} retrieved from a running SSH agent
 * like {@code ssh-agent} or {@code PAGEANT}. The communication with the agent is provided by {@link RemoteIdentityRepository} ({@code jsch-agent-proxy}).
 * <p>
 * Additionally, this factory also adds all unencrypted(!) {@code Identites} from the {@link JSch#getIdentityRepository() default JSch IdentityRepository}
 * (after the {@code Identites} from the agent).
 * </p>
 * <p>
 * Design note: This factory does <b>not</b> simply set {@link RemoteIdentityRepository} via {@link JSch#setIdentityRepository(IdentityRepository)} as
 * <a href="https://github.com/ymnk/jsch-agent-proxy/blob/master/examples/src/main/java/com/jcraft/jsch/agentproxy/examples/JSchWithAgentProxy.java#L32">
 * advertised</a> by {@code jsch-agent-proxy}. The reason for that is the following special handling of default {@code Identites} (like {@code ~/.ssh/id_rsa})
 * <i>in case those {@code Identites} are encrypted</i> (which is typically the case when agent access is needed):
 * <ul>
 * <li>{@link IdentityRepository.Wrapper}: contains a cache that is consulted <i>before</i> the {@code Identites} from the wrapped repo (unencrpyted, from the
 * agent) and since the cache contains encrypted {@code Identites}, authentication will fail</li>
 * <li>{@link JSch#addIdentity(Identity, byte[])}: created the {@code Wrapper} on demand</li>
 * </ul>
 * Therefore this factory sets an individual {@link IdentityRepository} for each {@link Session} (for which no such wrapping happens). This repository is also
 * read-only to prevent undesired write access to the agent.
  */
public class AgentProxyAwareJschConfigSessionFactory extends JschConfigSessionFactory {

    private Logger logger = LoggerFactory.getLogger(AgentProxyAwareJschConfigSessionFactory.class);

    @Override
    protected void configure(Host hc, Session session) {
        // nothing to do
    }

    @Override
    protected Session createSession(Host hc, String user, String host, int port, FS fs)
            throws JSchException {
        JSch jSch = getJSch(hc, fs);

        // assumption: identities from agent are always unencrypted
        final Collection<Identity> allUnencryptedIdentities = getIdentitiesFromAgentProxy();

        @SuppressWarnings("unchecked")
        Collection<Identity> identities = ((Collection<Identity>) jSch.getIdentityRepository().getIdentities());
        identities.stream()
                .filter(id -> !id.isEncrypted())
                .forEach(allUnencryptedIdentities::add);
        
        Session session = jSch.getSession(user, host, port);
        session.setIdentityRepository(new ReadOnlyIdentityRepository(allUnencryptedIdentities));
        return session;
    }

    private Collection<Identity> getIdentitiesFromAgentProxy() {

        Connector con = null;
        try {
            con = ConnectorFactory.getDefault().createConnector();
        } catch(AgentProxyException e) {
            logger.warn("AgentProxy setup failed, cannot read identities from agent", e);
        }
        return con != null ? new RemoteIdentityRepository(con).getIdentities() : new ArrayList<>();
    }

    private static class ReadOnlyIdentityRepository implements IdentityRepository {

        private final Collection<Identity> allUnencryptedIdentities;

        private ReadOnlyIdentityRepository(Collection<Identity> allUnencryptedIdentities) {
            this.allUnencryptedIdentities = allUnencryptedIdentities;
        }

        @Override
        public void removeAll() {
        }

        @Override
        public boolean remove(byte[] blob) {
            return false;
        }

        @Override
        public int getStatus() {
            return IdentityRepository.RUNNING;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
        public Vector<Identity> getIdentities() {
            return new Vector<>(allUnencryptedIdentities);
        }

        @Override
        public boolean add(byte[] identity) {
            return false;
        }
    }
}

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.DaemonClient;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RepoResolver implements RepositoryResolver<DaemonClient>, AutoCloseable {

    private static final String DOT_GIT = ".git";
    private Map<String, Repository> repositories = new HashMap<>();
    private final File repoDir;
    private final boolean bare;

    public RepoResolver(File repoDir, boolean bare) {
        this.repoDir = repoDir;
        this.bare = bare;
    }

    @Override
    public Repository open(DaemonClient client, String name)
            throws RepositoryNotFoundException,
            ServiceNotAuthorizedException, ServiceNotEnabledException,
            ServiceMayNotContinueException {
        return getOrCreateRepo(name);
    }

    private Repository getOrCreateRepo(String name) {
        if (!repositories.containsKey(name)) {
            repositories.put(name, createRepo());
        }
        return repositories.get(name);
    }

    private Repository createRepo() {
        Repository repo;
        try {
            if (bare) {
                repo = new FileRepositoryBuilder().setGitDir(repoDir).build();
                repo.create(true);
            } else {
                repo = new FileRepositoryBuilder()
                        .setGitDir(new File(repoDir, DOT_GIT))
                        .setMustExist(true).build();
            }
            repo.scanForRepoChanges();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return repo;
    }

    @Override
    public void close() throws Exception {
        for (Map.Entry<String, Repository> repository: repositories.entrySet()) {
            repository.getValue().close();
        }
    }
}

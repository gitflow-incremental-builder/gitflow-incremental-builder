package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.AgentProxyAwareJschConfigSessionFactory;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.control.jgit.HttpDelegatingCredentialsProvider;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Named
public class DifferentFiles {

    public static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private static final String HEAD = "HEAD";
    private static final String REFS_REMOTES = "refs/remotes/";
    private static final String REFS_HEADS = "refs/heads/";

    private Logger logger = LoggerFactory.getLogger(DifferentFiles.class);

    @Inject private GitProvider gitProvider;

    private final Map<String, String> additionalNativeGitEnvironment = new HashMap<>();

    public Set<Path> get(Configuration config) throws GitAPIException, IOException {
        Set<Path> paths = new HashSet<>();

        Worker worker = null;
        try {
            worker = new Worker(gitProvider.get(config), config);

            worker.fetch();
            worker.checkout();
            if (!config.disableBranchComparison) {
                paths.addAll(worker.getBranchDiff());
            }
            if (config.uncommited || config.untracked) {
                paths.addAll(worker.getChangesFromStatus());
            }
        } finally {
            if (worker != null) {
                worker.credentialsProvider.resetAll();
            }
        }
        return paths;
    }

    /**
     * Only for testing!
     *
     * @param key environment key for {@link HttpDelegatingCredentialsProvider#DelegatingCredentialsProvider(Path, Map)}
     * @param value environment value for {@link HttpDelegatingCredentialsProvider#DelegatingCredentialsProvider(Path, Map)}
     */
    void putAdditionalNativeGitEnvironment(String key, String value) {
        additionalNativeGitEnvironment.put(key, value);
    }

    private class Worker {

        private final Git git;
        private final Path workTree;
        private final Configuration configuration;
        private final HttpDelegatingCredentialsProvider credentialsProvider;

        public Worker(Git git, Configuration configuration) {
            this.git = git;
            this.workTree = git.getRepository().getWorkTree().toPath().normalize().toAbsolutePath();
            this.configuration = configuration;
            this.credentialsProvider = new HttpDelegatingCredentialsProvider(workTree, additionalNativeGitEnvironment);
        }

        private Set<Path> getBranchDiff() throws IOException {
            RevCommit base = getBranchCommit(configuration.baseBranch);
            final TreeWalk treeWalk = new TreeWalk(git.getRepository());
            try {
                treeWalk.addTree(base.getTree());
                treeWalk.addTree(resolveReference(base).getTree());
                treeWalk.setFilter(TreeFilter.ANY_DIFF);
                treeWalk.setRecursive(true);
                return getDiff(treeWalk, workTree);
            } finally {
                treeWalk.close();
            }
        }

        private void checkout() throws IOException, GitAPIException {
            if (! (HEAD.equals(configuration.baseBranch) || configuration.baseBranch.startsWith("worktrees/")) && ! git.getRepository().getFullBranch().equals(configuration.baseBranch)) {
                logger.info("Checking out base branch " + configuration.baseBranch + "...");
                git.checkout().setName(configuration.baseBranch).call();
            }
        }

        private void fetch() throws GitAPIException {
            if (!configuration.disableBranchComparison && configuration.fetchReferenceBranch) {
                fetch(configuration.referenceBranch);
            }
            if (configuration.fetchBaseBranch) {
                fetch(configuration.baseBranch);
            }
        }

        private void fetch(String branchName) throws GitAPIException {
            logger.info("Fetching branch " + branchName);
            if (!branchName.startsWith(REFS_REMOTES)) {
                throw new IllegalArgumentException("Branch name '" + branchName + "' is not tracking branch name since it does not start " + REFS_REMOTES);
            }
            String remoteName = extractRemoteName(branchName);
            String shortName = extractShortName(remoteName, branchName);
            FetchCommand fetchCommand = git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(remoteName)
                    .setRefSpecs(new RefSpec(REFS_HEADS + shortName + ":" + branchName));
            if (configuration.useJschAgentProxy) {
                fetchCommand.setTransportConfigCallback(transport -> {
                    if (transport instanceof SshTransport) {
                        ((SshTransport) transport).setSshSessionFactory(new AgentProxyAwareJschConfigSessionFactory());
                    }
                });
            }
            fetchCommand.call();
        }

        private String extractRemoteName(String branchName) {
            return branchName.split("/")[2];
        }

        private String extractShortName(String remoteName, String branchName) {
            return branchName.replaceFirst(REFS_REMOTES + remoteName + "/", "");
        }

        private RevCommit getMergeBase(RevCommit baseCommit, RevCommit referenceHeadCommit) throws IOException {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.lookupCommit(baseCommit));
            walk.markStart(walk.lookupCommit(referenceHeadCommit));
            RevCommit commit = walk.next();
            walk.close();
            logger.info("Using merge base of id: " + commit.getId());
            return commit;
        }

        private Set<Path> getDiff(TreeWalk treeWalk, Path gitDir) throws IOException {
            final Set<Path> paths = new HashSet<>();
            while (treeWalk.next()) {
                Path path = Paths.get(treeWalk.getPathString()).normalize();
                if (pathIncluded(path)) {
                    paths.add(gitDir.resolve(path));
                }
            }
            return paths;
        }

        private RevCommit getBranchCommit(String branchName) throws IOException {
            Repository repository = git.getRepository();
            ObjectId objectId = repository.resolve(branchName);

            if (objectId == null) {
                if (repository.simplify(branchName) != null) {
                    throw new SkipExecutionException("Could not get Git ObjectId for branch of name '" + branchName
                            + "'. Is this repository empty (no commits yet)?");
                }
                if (branchName.startsWith(REFS_REMOTES) && repository.getRemoteNames().isEmpty()) {
                    throw new SkipExecutionException("Could not get Git ObjectId for branch of name '" + branchName
                            + "'. No remotes found at all, push still pending?");
                }
                throw new IllegalArgumentException("Git branch of name '" + branchName + "' not found.");
            }
            final RevWalk walk = new RevWalk(repository);
            RevCommit commit = walk.parseCommit(objectId);
            walk.close();
            logger.info("Reference commit of branch " + branchName + " is commit of id: " + commit.getId());
            return commit;
        }

        private Set<Path> getChangesFromStatus() throws GitAPIException {
            Set<String> changes = new HashSet<>();
            Status status = git.status().call();
            if (configuration.uncommited) {
                changes.addAll(status.getUncommittedChanges());
            }
            if (configuration.untracked) {
                changes.addAll(status.getUntracked());
            }
            return changes.stream()
                    .map(Paths::get)
                    .map(Path::normalize)
                    .filter(this::pathIncluded)
                    .map(workTree::resolve)
                    .collect(Collectors.toSet());
        }

        private RevCommit resolveReference(RevCommit base) throws IOException {
            RevCommit refHead = getBranchCommit(configuration.referenceBranch);
            if (configuration.compareToMergeBase) {
                return getMergeBase(base, refHead);
            } else {
                return refHead;
            }
        }

        private boolean pathIncluded(Path path) {
            final String pathString = path.toString();
            boolean excluded = configuration.excludePathRegex.map(pred -> pred.test(pathString)).orElse(false);
            boolean included = !excluded && configuration.includePathRegex.map(pred -> pred.test(pathString)).orElse(true);
            logger.debug("included {}: {}", included, pathString);
            return included;
        }
    }
}

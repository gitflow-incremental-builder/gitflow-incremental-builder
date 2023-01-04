package io.github.gitflowincrementalbuilder.jgit;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.SkipExecutionException;
import io.github.gitflowincrementalbuilder.config.Configuration;

@Singleton
@Named
public class DifferentFiles {

    private static final String HEAD = "HEAD";
    private static final String REFS_REMOTES = "refs/remotes/";
    private static final String REFS_HEADS = "refs/heads/";
    private static final String REFS_TAGS = "refs/tags/";

    private Logger logger = LoggerFactory.getLogger(DifferentFiles.class);

    @Inject private GitProvider gitProvider;

    private final Map<String, String> additionalNativeGitEnvironment = new HashMap<>();

    public Set<Path> get(Configuration config) {
        Set<Path> paths = new HashSet<>();

        Worker worker = null;
        try {
            worker = new Worker(gitProvider.get(config), config);

            worker.fetch();
            worker.checkout();
            if (!config.disableBranchComparison) {
                paths.addAll(worker.getBranchDiff());
            }
            if (config.uncommitted || config.untracked) {
                paths.addAll(worker.getChangesFromStatus());
            }
        } catch (GitAPIException | IOException e) {
            throw new IllegalStateException("Failed to get file differences", e);
        } finally {
            if (worker != null) {
                worker.credentialsProvider.resetAll();
            }
        }
        if (!paths.isEmpty() && logger.isDebugEnabled()) {
            logger.debug("Changed files:\n\t" + paths.stream().map(Path::toString).collect(Collectors.joining("\n\t")));
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
            RevCommit base = getBranchCommit(configuration.baseBranch, false);
            try (final TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(base.getTree());
                treeWalk.addTree(resolveReference(base).getTree());
                treeWalk.setFilter(TreeFilter.ANY_DIFF);
                treeWalk.setRecursive(true);
                return getDiff(treeWalk, workTree);
            }
        }

        private void checkout() throws IOException, GitAPIException {
            if (! (HEAD.equals(configuration.baseBranch) || configuration.baseBranch.startsWith("worktrees/")) && ! git.getRepository().getFullBranch().equals(configuration.baseBranch)) {
                logger.info("Checking out base branch " + configuration.baseBranch);
                git.checkout().setName(configuration.baseBranch).call();
            }
        }

        private void fetch() throws GitAPIException {
            if (!configuration.disableBranchComparison && configuration.fetchReferenceBranch) {
                fetch(configuration.referenceBranch, true);
            }
            if (configuration.fetchBaseBranch) {
                fetch(configuration.baseBranch, false);
            }
        }

        private void fetch(String branchName, boolean reference) throws GitAPIException {
            logger.info("Fetching " + branchName);
            final String remoteName;
            final String spec;
            if (branchName.startsWith(REFS_TAGS)) {
                remoteName = getSingleRemoteName();
                spec = branchName + ":" + branchName;
            } else if (!branchName.startsWith(REFS_REMOTES)) {
                throw new IllegalArgumentException("Cannot fetch local " + (reference ? "reference" : "base") + " branch '" + branchName + "'. "
                        + "Only remote tracking branches can be fetched, meaning branches starting with '" + REFS_REMOTES + "'. "
                        + "Make sure to not confuse remote tracking branches with local branches, 'git branch -a' is your friend!");
            } else {
                remoteName = extractRemoteName(branchName);
                spec = REFS_HEADS + extractShortName(remoteName, branchName) + ":" + branchName;
            }
            git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(remoteName)
                    .setRefSpecs(new RefSpec(spec))
                    .call();
        }

        private String getSingleRemoteName() {
            Set<String> remoteNames = git.getRepository().getRemoteNames();
            return remoteNames.size() == 1 ? remoteNames.iterator().next() : Constants.DEFAULT_REMOTE_NAME;
        }

        private String extractRemoteName(String branchName) {
            return branchName.split("/")[2];
        }

        private String extractShortName(String remoteName, String branchName) {
            return branchName.replaceFirst(REFS_REMOTES + remoteName + "/", "");
        }

        private RevCommit getMergeBase(RevCommit baseCommit, RevCommit referenceHeadCommit) throws IOException {
            try (final RevWalk walk = new RevWalk(git.getRepository())) {
                walk.setRevFilter(RevFilter.MERGE_BASE);
                walk.markStart(walk.lookupCommit(baseCommit));
                walk.markStart(walk.lookupCommit(referenceHeadCommit));
                RevCommit commit = walk.next();
                if (commit == null) {
                    throw new IllegalStateException(String.format(
                            "Cannot find merge base, try fetching more history.%n\tbase: %s%n\treference: %s",
                            baseCommit, referenceHeadCommit));
                }
                logger.info("Using merge base of id: " + commit.getId());
                return commit;
            }
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

        private RevCommit getBranchCommit(String branchName, boolean reference) throws IOException {
            Repository repository = git.getRepository();
            ObjectId objectId = repository.resolve(branchName);

            boolean isRemoteTrackingBranch = branchName.startsWith(REFS_REMOTES);
            String branchDesc = String.format("%s %s branch '%s'",
                    isRemoteTrackingBranch ? "remote tracking" : "local",
                    reference ? "reference" : "base",
                    branchName);
            if (objectId == null) {
                if (repository.simplify(branchName) != null) {
                    throw new SkipExecutionException("Could not get Git ObjectId for " + branchDesc
                            + ". Is this repository empty (no commits yet)?");
                }
                if (isRemoteTrackingBranch && repository.getRemoteNames().isEmpty()) {
                    throw new SkipExecutionException("Could not get Git ObjectId for " + branchDesc
                            + ". No remotes found at all, push still pending?");
                }
                throw new IllegalArgumentException("Git " + branchDesc + " not found. "
                        + "Make sure it exists by creating it explicitly via 'git branch/checkout/switch ...' "
                        + "or by fetching it from the remote repository via 'git fetch <remote> <branch>:<branch>' (or just 'git fetch <branch>'). "
                        + "Also make sure to not confuse remote tracking branches (refs/remotes/...) with local branches, 'git branch -a' is your friend!");
            }
            try (final RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(objectId);
                logger.info("Reference commit of " + branchDesc + " has id: " + commit.getId());
                return commit;
            }
        }

        private Set<Path> getChangesFromStatus() throws GitAPIException {
            Set<String> changes = new HashSet<>();
            Status status = git.status().call();
            if (configuration.uncommitted) {
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
            RevCommit refHead = getBranchCommit(configuration.referenceBranch, true);
            if (configuration.compareToMergeBase) {
                return getMergeBase(base, refHead);
            } else {
                return refHead;
            }
        }

        private boolean pathIncluded(Path path) {
            final String pathString = path.toString();
            if (configuration.skipIfPathMatches.map(pred -> pred.test(pathString)).orElse(false)) {
                throw new SkipExecutionException("Changed path matches regex defined by skipIfPathMatches: " + pathString);
            }
            boolean excluded = configuration.excludePathsMatching.map(pred -> pred.test(pathString)).orElse(false);
            boolean included = !excluded && configuration.includePathsMatching.map(pred -> pred.test(pathString)).orElse(true);
            logger.debug("included {}: {}", included, pathString);
            return included;
        }
    }
}

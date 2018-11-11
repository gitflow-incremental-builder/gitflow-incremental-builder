package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Named("gib.differentFiles")
public class DifferentFiles {

    public static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private static final String HEAD = "HEAD";
    private static final String REFS_REMOTES = "refs/remotes/";
    private static final String REFS_HEADS = "refs/heads/";

    @Inject private Logger logger;
    @Inject private MavenSession mavenSession;
    @Inject private Configuration.Provider configProvider;

    public Set<Path> get() throws GitAPIException, IOException {
        Set<Path> paths = new HashSet<>();

        Configuration configuration = configProvider.get();
        Git git = setupGit(configuration);
        try {
            Worker worker = new Worker(git, configuration);

            worker.fetch();
            worker.checkout();
            if (!configuration.disableBranchComparison) {
                paths.addAll(worker.getBranchDiff());
            }
            if (configuration.uncommited || configuration.untracked) {
                paths.addAll(worker.getChangesFromStatus());
            }
        } finally {
            git.getRepository().close();
            git.close();
        }
        return paths;
    }

    private Git setupGit(Configuration configuration) throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = mavenSession.getCurrentProject().getBasedir().toPath().toFile();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            String gitDirNotFoundMessage = "Git repository root directory not found ascending from current working directory:'" + pomDir + "'.";
            logger.warn(gitDirNotFoundMessage + " Next step is determined by failOnMissingGitDir property.");
            if (configuration.failOnMissingGitDir) {
                throw new IllegalArgumentException(gitDirNotFoundMessage);
            } else {
                throw new SkipExecutionException(gitDirNotFoundMessage);
            }
        }
        if (isWorktree(builder)) {
            throw new SkipExecutionException(UNSUPPORTED_WORKTREE + builder.getGitDir());
        }
        return Git.wrap(builder.build());
    }

    private boolean isWorktree(FileRepositoryBuilder builder) {
        Path gitDirParent = builder.getGitDir().toPath().getParent();
        return gitDirParent.getFileName().toString().equals("worktrees")
                && gitDirParent.getParent().getFileName().toString().equals(".git");
    }

    private class Worker {

        private final Git git;
        private final Path workTree;
        private final Configuration configuration;

        public Worker(Git git, Configuration configuration) {
            this.git = git;
            this.workTree = git.getRepository().getWorkTree().toPath().normalize().toAbsolutePath();
            this.configuration = configuration;
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
            git.fetch().setRemote(remoteName).setRefSpecs(new RefSpec(REFS_HEADS + shortName + ":" + branchName)).call();
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
                Path path = gitDir.resolve(treeWalk.getPathString()).normalize();
                if (! configuration.excludePathRegex.test(path.toString())) {
                    paths.add(path);
                }
            }
            return paths;
        }

        private RevCommit getBranchCommit(String branchName) throws IOException {
            ObjectId objectId = git.getRepository().resolve(branchName);

            if (objectId == null) {
                throw new IllegalArgumentException("Git branch of name '" + branchName + "' not found.");
            }
            final RevWalk walk = new RevWalk(git.getRepository());
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
            return changes.stream().map(workTree::resolve).map(Path::normalize).collect(Collectors.toSet());
        }

        private RevCommit resolveReference(RevCommit base) throws IOException {
            RevCommit refHead = getBranchCommit(configuration.referenceBranch);
            if (configuration.compareToMergeBase) {
                return getMergeBase(base, refHead);
            } else {
                return refHead;
            }
        }
    }
}

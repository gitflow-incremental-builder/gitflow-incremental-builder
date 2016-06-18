package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DifferentFiles {

    private static final String HEAD = "HEAD";
    @Inject private Git git;
    @Inject private Configuration configuration;
    @Inject private Logger logger;

    public Set<Path> list() throws GitAPIException, IOException {
        if (configuration.fetchReferenceBranch) {
            logger.info("Fetching reference branch " + configuration.referenceBranch);
            git.fetch().setRefSpecs(new RefSpec(":" + configuration.referenceBranch));
        }
        // TODO Fetch base branch
        if (! HEAD.equals(configuration.baseBranch) && ! git.getRepository().getFullBranch().equals(configuration.baseBranch)) {
            logger.info("Checking out base branch " + configuration.baseBranch + "...");
            git.checkout().setName(configuration.baseBranch).call();
        }
        // TODO extract method to be called with commits to be compared
        final TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(getBranchHead(configuration.baseBranch).getTree());
        if (configuration.compareToMergeBase) {
            treeWalk.addTree(getMergeBase().getTree());
        } else {
            treeWalk.addTree(getBranchHead(configuration.referenceBranch).getTree());
        }
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        treeWalk.setRecursive(true);
        final Path gitDir = Paths.get(git.getRepository().getDirectory().getCanonicalPath()).getParent();
        final Set<Path> paths = getDiff(treeWalk, gitDir);
        if (configuration.uncommited) {
            paths.addAll(getUncommitedChanges(gitDir));
        }
        git.getRepository().close();
        git.close();
        return paths;
    }

    private RevCommit getMergeBase() throws IOException {
        RevWalk walk = new RevWalk(git.getRepository());
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(getBranchHead(configuration.baseBranch));
        walk.markStart(getBranchHead(configuration.referenceBranch));
        RevCommit commit = walk.next();
        logger.info("Using merge base of id: " + commit.getId());
        return commit;
    }

    private Set<Path> getDiff(TreeWalk treeWalk, Path gitDir) throws IOException {
        final Set<Path> paths = new HashSet<>();
        while (treeWalk.next()) {
            paths.add(gitDir.resolve(treeWalk.getPathString()).normalize());
        }
        return paths;
    }

    private RevCommit getBranchHead(String branchName) throws IOException {
        final Map<String, Ref> allRefs = git.getRepository().getAllRefs();
        final RevWalk walk = new RevWalk(git.getRepository());
        Ref ref = allRefs.get(branchName);
        if (ref == null) {
            throw new IllegalArgumentException("Git branch of name '" + branchName + "' not found.");
        }
        RevCommit commit = walk.parseCommit(ref.getObjectId());
        logger.info("Head of branch " + branchName + " is commit of id: " + commit.getId());
        return commit;
    }

    private Set<Path> getUncommitedChanges(Path gitDir) throws GitAPIException {
        return git.status().call().getUncommittedChanges().stream()
                .map(gitDir::resolve).map(Path::normalize).collect(Collectors.toSet());
    }

}

package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.boundary.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
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

    @Inject private Git git;
    @Inject private Properties properties;

    public Set<Path> list() throws GitAPIException, IOException {
        final TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(getBranchTree(git, properties.branch));
        treeWalk.addTree(getBranchTree(git, properties.referenceBranch));
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        treeWalk.setRecursive(true);
        final Path gitDir = Paths.get(git.getRepository().getDirectory().getCanonicalPath()).getParent();
        final Set<Path> paths = getDiff(treeWalk, gitDir);
        if (properties.uncommited) {
            paths.addAll(getUncommitedChanges(gitDir));
        }
        git.getRepository().close();
        git.close();
        return paths;
    }

    private Set<Path> getDiff(TreeWalk treeWalk, Path gitDir) throws IOException {
        final Set<Path> paths = new HashSet<>();
        while (treeWalk.next()) {
            paths.add(gitDir.resolve(treeWalk.getPathString()).normalize());
        }
        return paths;
    }

    private RevTree getBranchTree(Git git, String branchName) throws IOException {
        final Map<String, Ref> allRefs = git.getRepository().getAllRefs();
        final RevWalk walk = new RevWalk(git.getRepository());
        final RevCommit commit = walk.parseCommit(allRefs.get(branchName).getObjectId());
        return commit.getTree();
    }

    private Set<Path> getUncommitedChanges(Path gitDir) throws GitAPIException {
        return git.status().call().getUncommittedChanges().stream()
                .map(gitDir::resolve).map(Path::normalize).collect(Collectors.toSet());
    }

}

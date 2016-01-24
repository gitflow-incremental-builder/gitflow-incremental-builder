package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiffLister {
    public static List<Path> act() throws GitAPIException, IOException {
        final String workDir = System.getProperty("user.dir");
        Git git = new Git(new FileRepository(new File(workDir + "/.git" )));
        git.fetch().call();
        final TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(getBranchTree(git, "HEAD"));
        treeWalk.addTree(getBranchTree(git, "refs/remotes/origin/develop"));
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        treeWalk.setRecursive(true);
        final ArrayList<Path> paths = new ArrayList<>();
        while (treeWalk.next()) {
            paths.add(Paths.get(workDir + "/" + treeWalk.getPathString()));
        }
        return paths;
    }

    private static RevTree getBranchTree(Git git, String branchName) throws IOException {
        final Map<String, Ref> allRefs = git.getRepository().getAllRefs();
        final RevWalk walk = new RevWalk(git.getRepository());
        final RevCommit commit = walk.parseCommit(allRefs.get(branchName).getObjectId());
        return commit.getTree();
    }

    // Milestone: Collect archetype names
    // input: vcs root with feature branch as current branch.
    // Execute fetch if stable branch is pointed to a remote branch..
    // Compare the two branches either as push-changes or complete diff.
    // Find maven module archetype names

    // Milestone: Reimplement into Maven plugin

    // Don't compare push changes because incase of local build one could run into problems.
    // Rather build new version.


    // Implement first as a command line tool that returns project names.
    // Next attempt to integrate as a plugin into maven.

    // https://maven.apache.org/ref/3.3.9/maven-plugin-api/apidocs/index.html
    // https://stackoverflow.com/questions/5984423/how-do-i-run-a-maven-plugin-on-all-modules

}

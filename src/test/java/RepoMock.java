import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

public class RepoMock {

    @Test
    public void init() throws Exception {
        final RemoteRepoMock remoteRepo = new RemoteRepoMock(false);
        new UnZiper().act(new File("src/test/resources/template.zip"), new File("tmp/repo"));
        Git git = new Git(new FileRepository(new File("tmp/repo/.git")));
        configureRemote(git);
        git.fetch().call();
        final TreeWalk treeWalk = new TreeWalk(git.getRepository());
        treeWalk.addTree(getBranchTree(git, "HEAD"));
        treeWalk.addTree(getBranchTree(git, "refs/remotes/origin/develop"));
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        Assert.assertTrue(treeWalk.next());
        Assert.assertEquals("", treeWalk.getPathString());
        remoteRepo.close();
    }

    private RevTree getBranchTree(Git git, String branchName) throws IOException {
        final Map<String, Ref> allRefs = git.getRepository().getAllRefs();
        final RevWalk walk = new RevWalk(git.getRepository());
        final RevCommit commit = walk.parseCommit(allRefs.get(branchName).getObjectId());
        return commit.getTree();
    }

    private void configureRemote(Git git) throws URISyntaxException, IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin" ,"fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("remote", "origin" ,"push", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("branch", "master", "remote", "origin");
        config.setString("branch", "master", "merge", "refs/heads/master");
        config.setString("push", null, "default", "current");
        RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
        URIish uri = new URIish(RemoteRepoMock.REPO_URL);
        remoteConfig.addURI(uri);
        remoteConfig.addFetchRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.addPushRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.update(config);
        config.save();
    }
}

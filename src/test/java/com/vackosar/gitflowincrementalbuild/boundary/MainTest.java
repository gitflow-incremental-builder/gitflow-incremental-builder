package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MainTest extends RepoTest {

    @Test
    public void list() throws Exception {
        execute(LocalRepoMock.WORK_DIR + "/parent/pom.xml");
    }

    @Test
    public void listRelatively() throws Exception {
        execute("parent/pom.xml");
    }

    @Test
    public void failOnTransport() throws IOException, URISyntaxException, GitAPIException {
        localRepoMock.invalidateConfigration();
        final ByteArrayOutputStream errout = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errout));
        execute("parent/pom.xml");
        Assert.assertTrue(errout.toString().contains("Failed to connect to the remote. Will rely on current state."));
    }

    @Test public void fetchOnlyDevelop() throws GitAPIException, IOException {
        final Git git = localRepoMock.getGit();
        git.branchDelete().setBranchNames("refs/remotes/origin/develop").setForce(true).call();
        git.branchDelete().setBranchNames("refs/remotes/origin/master").setForce(true).call();
        execute("parent/pom.xml");
        final Set<String> actual = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call().stream().map(Ref::getName).collect(Collectors.toSet());
        final Set<String> expected = new HashSet<>(Arrays.asList("refs/remotes/origin/feature/1", "refs/remotes/origin/develop"));
        Assert.assertEquals(expected, actual);
    }

    private String execute(String pom) throws GitAPIException, IOException {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        Main.main(new String[]{pom});
        Assert.assertEquals("child2\\subchild2,child3,child4" + System.lineSeparator(), stdout.toString());
        return stdout.toString();
    }
}

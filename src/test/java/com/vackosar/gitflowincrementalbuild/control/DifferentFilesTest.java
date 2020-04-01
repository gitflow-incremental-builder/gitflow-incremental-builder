package com.vackosar.gitflowincrementalbuild.control;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.Assert;
import org.junit.Test;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class DifferentFilesTest extends BaseDifferentFilesTest {

    private static final String REFS_HEADS_FEATURE_2 = "refs/heads/feature/2";
    private static final String HEAD = "HEAD";

    public DifferentFilesTest() {
        super(TestServerType.GIT_PROTOCOL);
    }

    @Test(expected = SkipExecutionException.class)
    public void worktree() throws Exception {
        Path workDir = temporaryFolder.getRoot().toPath().resolve("tmp/repo/wrkf2");

        invokeUnderTest(MavenSessionMock.get(workDir, projectProperties));
    }

    @Test
    public void listWithUncommitted() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.fullName(), "true");

        Assert.assertTrue(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUncommitted_disabled() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.fullName(), "false");

        Assert.assertFalse(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUncommitted_excluded() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.fullName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.fullName(), Pattern.quote(repoPath.relativize(modifiedFilePath).toString()));

        Assert.assertFalse(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUntracked() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.fullName(), "true");

        Assert.assertTrue(invokeUnderTest().contains(newFilePath));
    }

    @Test
    public void listWithUntracked_disabled() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.fullName(), "false");

        Assert.assertFalse(invokeUnderTest().contains(newFilePath));
    }

    @Test
    public void listWithUntracked_excluded() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.fullName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.fullName(), Pattern.quote(repoPath.relativize(newFilePath).toString()));

        Assert.assertFalse(invokeUnderTest().contains(newFilePath));
    }

    @Test
    public void listWithCheckout() throws Exception {
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        projectProperties.setProperty(Property.baseBranch.fullName(), "refs/heads/feature/2");
        projectProperties.setProperty(Property.baseBranch.fullName(), "refs/heads/feature/2");

        invokeUnderTest();

        verify(loggerSpy).info(contains("Checking out base branch refs/heads/feature/2"));
    }

    @Test
    public void list() throws Exception {
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(repoPath + "/parent/child3/src/resources/file1"),
                Paths.get(repoPath + "/parent/child4/pom.xml"),
                Paths.get(repoPath + "/parent/testJarDependent/src/resources/file5")
                ));

        Assert.assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listExcluding() throws Exception {
        projectProperties.setProperty(Property.excludePathRegex.fullName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child3/src/resources/file1"),
                Paths.get(repoPath + "/parent/child4/pom.xml"),
                Paths.get(repoPath + "/parent/testJarDependent/src/resources/file5")
        ));

        Assert.assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listIncluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.fullName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file22")
                ));

        Assert.assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listIncludingAndExcluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.fullName(), ".*file2.*");
        projectProperties.setProperty(Property.excludePathRegex.fullName(), ".*file22.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2")
                ));

        Assert.assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listWithDisabledBranchComparison() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.fullName(), "true");

        Assert.assertEquals(Collections.emptySet(), invokeUnderTest());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path dir = repoPath.resolve("parent/child2");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                dir.resolve("subchild2/src/resources/file2"),
                dir.resolve("subchild2/src/resources/file22"),
                dir.resolve("../child3/src/resources/file1").normalize(),
                dir.resolve("../child4/pom.xml").normalize(),
                dir.resolve("../testJarDependent/src/resources/file5").normalize()
        ));

        Assert.assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listComparedToMergeBase() throws Exception {
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        localRepoMock.getGit().checkout().setName(REFS_HEADS_FEATURE_2).call();
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        projectProperties.setProperty(Property.baseBranch.fullName(), REFS_HEADS_FEATURE_2);
        projectProperties.setProperty(Property.compareToMergeBase.fullName(), "true");

        Assert.assertTrue(invokeUnderTest().stream().anyMatch(repoPath.resolve("parent/feature2-only-file.txt")::equals));

        verify(loggerSpy).info(contains("59dc82fa887d9ca82a0d3d1790c6d767e738e71a"));
    }

    @Test
    public void fetch() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.fullName(), "true");
        projectProperties.setProperty(Property.referenceBranch.fullName(), REMOTE_DEVELOP);

        invokeUnderTest();

        Git localGit = localRepoMock.getGit();
        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }

    @Test
    public void fetchNonExistent() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        Git localGit = localRepoMock.getGit();
        localGit.branchDelete().setBranchNames(DEVELOP).call();
        localGit.branchDelete().setBranchNames(REMOTE_DEVELOP).call();
        projectProperties.setProperty(Property.fetchReferenceBranch.fullName(), "true");
        projectProperties.setProperty(Property.referenceBranch.fullName(), REMOTE_DEVELOP);

        invokeUnderTest();

        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }

    private Path modifyTrackedFile(Path repoPath) throws IOException {
        Path modifiedFilePath = repoPath.resolve("parent/child1/src/resources/file1");
        Files.write(modifiedFilePath, "\nuncommitted".getBytes(), StandardOpenOption.APPEND);
        return modifiedFilePath;
    }

    private Path createNewUntrackedFile(Path repoPath) throws IOException {
        Path newFilePath = repoPath.resolve("parent/child1/src/resources/fileNew");
        Files.write(newFilePath, "\nuncommitted".getBytes(), StandardOpenOption.CREATE_NEW);
        return newFilePath;
    }
}

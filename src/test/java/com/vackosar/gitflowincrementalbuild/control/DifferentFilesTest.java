package com.vackosar.gitflowincrementalbuild.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;
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

import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.Test;

import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import com.vackosar.gitflowincrementalbuild.mocks.EmptyLocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.MavenSessionMock;
import com.vackosar.gitflowincrementalbuild.mocks.server.TestServerType;

public class DifferentFilesTest extends BaseDifferentFilesTest {

    private static final String REFS_HEADS_FEATURE_2 = "refs/heads/feature/2";
    private static final String HEAD = "HEAD";

    public DifferentFilesTest() {
        super(TestServerType.GIT_PROTOCOL);
    }

    @Test
    public void worktree() throws Exception {
        Path workDir = repoBaseDir.resolve("tmp/repo/wrkf2");

        assertThatExceptionOfType(SkipExecutionException.class).isThrownBy(
                () -> invokeUnderTest(MavenSessionMock.get(workDir, projectProperties)));
    }

    @Test
    public void listWithUncommitted() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommitted.prefixedName(), "true");

        assertThat(invokeUnderTest().contains(modifiedFilePath)).isTrue();
    }

    @Test
    public void listWithUncommitted_disabled() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommitted.prefixedName(), "false");

        assertThat(invokeUnderTest().contains(modifiedFilePath)).isFalse();
    }

    @Test
    public void listWithUncommitted_excluded() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommitted.prefixedName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), Pattern.quote(repoPath.relativize(modifiedFilePath).toString()));

        assertThat(invokeUnderTest().contains(modifiedFilePath)).isFalse();
    }

    @Test
    public void listWithUntracked() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");

        assertThat(invokeUnderTest().contains(newFilePath)).isTrue();
    }

    @Test
    public void listWithUntracked_disabled() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "false");

        assertThat(invokeUnderTest().contains(newFilePath)).isFalse();
    }

    @Test
    public void listWithUntracked_excluded() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), Pattern.quote(repoPath.relativize(newFilePath).toString()));

        assertThat(invokeUnderTest().contains(newFilePath)).isFalse();
    }

    @Test
    public void listWithCheckout() throws Exception {
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        projectProperties.setProperty(Property.baseBranch.prefixedName(), "refs/heads/feature/2");

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

        assertThat(invokeUnderTest()).isEqualTo(expected);
    }

    @Test
    public void listExcluding() throws Exception {
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child3/src/resources/file1"),
                Paths.get(repoPath + "/parent/child4/pom.xml"),
                Paths.get(repoPath + "/parent/testJarDependent/src/resources/file5")
        ));

        assertThat(invokeUnderTest()).isEqualTo(expected);
    }

    @Test
    public void listIncluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.prefixedName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file22")
                ));

        assertThat(invokeUnderTest()).isEqualTo(expected);
    }

    @Test
    public void listIncludingAndExcluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.prefixedName(), ".*file2.*");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), ".*file22.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2")
                ));

        assertThat(invokeUnderTest()).isEqualTo(expected);
    }

    @Test
    public void listWithDisabledBranchComparison() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.prefixedName(), "true");

        assertThat(invokeUnderTest()).isEqualTo(Collections.emptySet());
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

        assertThat(invokeUnderTest()).isEqualTo(expected);
    }

    @Test
    public void listComparedToMergeBase() throws Exception {
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        localRepoMock.getGit().checkout().setName(REFS_HEADS_FEATURE_2).call();
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        projectProperties.setProperty(Property.baseBranch.prefixedName(), REFS_HEADS_FEATURE_2);
        projectProperties.setProperty(Property.compareToMergeBase.prefixedName(), "true");

        assertThat(invokeUnderTest().stream().anyMatch(repoPath.resolve("parent/feature2-only-file.txt")::equals)).isTrue();

        verify(loggerSpy).info(contains("59dc82fa887d9ca82a0d3d1790c6d767e738e71a"));
    }

    @Test
    public void fetch() throws Exception {
        addCommitToRemoteRepo(FETCH_FILE);
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), REMOTE_DEVELOP);

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
        projectProperties.setProperty(Property.fetchReferenceBranch.prefixedName(), "true");
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), REMOTE_DEVELOP);

        invokeUnderTest();

        localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
        localGit.checkout().setName(REMOTE_DEVELOP).call();
        assertCommitExists(FETCH_FILE, localGit);
    }

    @Test
    public void invalidBaseBranch() throws Exception {
        projectProperties.setProperty(Property.referenceBranch.prefixedName(), "FOO");

        Throwable thrown = catchThrowable(() -> invokeUnderTest());

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown).hasMessage("Git branch of name 'FOO' not found.");
    }

    @Test
    public void emptyLocalRepo() throws Exception {
        projectProperties.clear();  // special test that does not follow the pattern of the test base classes

        EmptyLocalRepoMock.withBasicPom(getRepoBaseDir(), emptyLocalRepoMock -> {
            MavenSession mavenSessionMock = MavenSessionMock.get(emptyLocalRepoMock.getRepoDir(), projectProperties);

            Throwable thrown = catchThrowable(() -> invokeUnderTest(mavenSessionMock));

            assertThat(thrown).isInstanceOf(SkipExecutionException.class);
            assertThat(thrown).hasMessageContaining("'HEAD'");
        });
    }

    @Test
    public void localRepoButNoRemoteRepo() throws Exception {
        projectProperties.clear();  // special test that does not follow the pattern of the test base classes

        EmptyLocalRepoMock.withBasicPom(getRepoBaseDir(), emptyLocalRepoMock -> {
            emptyLocalRepoMock.getGit().add().addFilepattern("pom.xml").call();
            emptyLocalRepoMock.getGit().commit().setMessage("initial commit with pom.xml").call();
            MavenSession mavenSessionMock = MavenSessionMock.get(emptyLocalRepoMock.getRepoDir(), projectProperties);

            Throwable thrown = catchThrowable(() -> invokeUnderTest(mavenSessionMock));

            assertThat(thrown).isInstanceOf(SkipExecutionException.class);
            assertThat(thrown).hasMessageContaining(Property.referenceBranch.getDefaultValue());
        });
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

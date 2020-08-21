package com.vackosar.gitflowincrementalbuild.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
        Path workDir = tempDir.resolve("tmp/repo/wrkf2");

        assertThatExceptionOfType(SkipExecutionException.class).isThrownBy(
                () -> invokeUnderTest(MavenSessionMock.get(workDir, projectProperties)));
    }

    @Test
    public void listWithUncommitted() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.prefixedName(), "true");

        assertTrue(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUncommitted_disabled() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.prefixedName(), "false");

        assertFalse(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUncommitted_excluded() throws Exception {
        Path modifiedFilePath = modifyTrackedFile(repoPath);
        projectProperties.setProperty(Property.uncommited.prefixedName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), Pattern.quote(repoPath.relativize(modifiedFilePath).toString()));

        assertFalse(invokeUnderTest().contains(modifiedFilePath));
    }

    @Test
    public void listWithUntracked() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");

        assertTrue(invokeUnderTest().contains(newFilePath));
    }

    @Test
    public void listWithUntracked_disabled() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "false");

        assertFalse(invokeUnderTest().contains(newFilePath));
    }

    @Test
    public void listWithUntracked_excluded() throws Exception {
        Path newFilePath = createNewUntrackedFile(repoPath);
        projectProperties.setProperty(Property.untracked.prefixedName(), "true");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), Pattern.quote(repoPath.relativize(newFilePath).toString()));

        assertFalse(invokeUnderTest().contains(newFilePath));
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

        assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listExcluding() throws Exception {
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child3/src/resources/file1"),
                Paths.get(repoPath + "/parent/child4/pom.xml"),
                Paths.get(repoPath + "/parent/testJarDependent/src/resources/file5")
        ));

        assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listIncluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.prefixedName(), ".*file2.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file22")
                ));

        assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listIncludingAndExcluding() throws Exception {
        projectProperties.setProperty(Property.includePathRegex.prefixedName(), ".*file2.*");
        projectProperties.setProperty(Property.excludePathRegex.prefixedName(), ".*file22.*");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(repoPath + "/parent/child2/subchild2/src/resources/file2")
                ));

        assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listWithDisabledBranchComparison() throws Exception {
        projectProperties.setProperty(Property.disableBranchComparison.prefixedName(), "true");

        assertEquals(Collections.emptySet(), invokeUnderTest());
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

        assertEquals(expected, invokeUnderTest());
    }

    @Test
    public void listComparedToMergeBase() throws Exception {
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        localRepoMock.getGit().checkout().setName(REFS_HEADS_FEATURE_2).call();
        localRepoMock.getGit().reset().setRef(HEAD).setMode(ResetCommand.ResetType.HARD).call();
        projectProperties.setProperty(Property.baseBranch.prefixedName(), REFS_HEADS_FEATURE_2);
        projectProperties.setProperty(Property.compareToMergeBase.prefixedName(), "true");

        assertTrue(invokeUnderTest().stream().anyMatch(repoPath.resolve("parent/feature2-only-file.txt")::equals));

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
        projectProperties.clear();  // special tests that does not follow the pattern of the test base classes
        try (InputStream basicPomData = DifferentFilesTest.class.getResourceAsStream("/DifferentFilesTest/pom.xml");
                EmptyLocalRepoMock emptyLocalRepoMock = new EmptyLocalRepoMock(getRepoBaseFolder())) {
            File projectFolder = emptyLocalRepoMock.getBaseCanonicalBaseFolder();
            Files.copy(basicPomData, new File(projectFolder, "pom.xml").toPath(), StandardCopyOption.REPLACE_EXISTING);
            MavenSession mavenSessionMock = MavenSessionMock.get(projectFolder.toPath(), projectProperties);

            Throwable thrown = catchThrowable(() -> invokeUnderTest(mavenSessionMock));

            assertThat(thrown).isInstanceOf(SkipExecutionException.class);
            assertThat(thrown).hasMessageContaining("'HEAD'");
        }
    }

    @Test
    public void localRepoButNoRemoteRepo() throws Exception {
        projectProperties.clear();  // special tests that does not follow the pattern of the test base classes
        try (InputStream basicPomData = DifferentFilesTest.class.getResourceAsStream("/DifferentFilesTest/pom.xml");
                EmptyLocalRepoMock emptyLocalRepoMock = new EmptyLocalRepoMock(getRepoBaseFolder())) {
            File projectFolder = emptyLocalRepoMock.getBaseCanonicalBaseFolder();
            Files.copy(basicPomData, new File(projectFolder, "pom.xml").toPath(), StandardCopyOption.REPLACE_EXISTING);
            emptyLocalRepoMock.getGit().add().addFilepattern("pom.xml").call();
            emptyLocalRepoMock.getGit().commit().setMessage("initial commit with pom.xml").call();
            MavenSession mavenSessionMock = MavenSessionMock.get(projectFolder.toPath(), projectProperties);

            Throwable thrown = catchThrowable(() -> invokeUnderTest(mavenSessionMock));

            assertThat(thrown).isInstanceOf(SkipExecutionException.class);
            assertThat(thrown).hasMessageContaining(Property.referenceBranch.getDefaultValue());
        }
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

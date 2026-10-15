package io.github.gitflowincrementalbuilder.jgit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.mocks.MavenSessionMock;
import io.github.gitflowincrementalbuilder.mocks.UnZipper;

/**
 * Tests a linked worktree of a <b>bare</b> repository, see
 * <a href="https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/issues/1238">#1238</a>.
 * <p>
 * <code>bare-worktree-template.zip</code> contains:
 * <ul>
 * <li><code>bare.git</code>: bare repo with branches <code>master</code> (2 child modules) and <code>feature</code>
 *     (adds <code>child1/feature-file.txt</code>)</li>
 * <li><code>wt</code>: linked worktree of <code>bare.git</code>, checked out at <code>feature</code></li>
 * </ul>
 * How the zip was created: see <code>bare-worktree-template.README.md</code> next to it in <code>src/test/resources</code>.
 * <p>
 * The worktree links are absolute paths (<code>wt/.git</code> and <code>bare.git/worktrees/wt/gitdir</code>), so they
 * are rewritten after unzipping, everything else is untouched.
 */
@ExtendWith(MockitoExtension.class)
public class DifferentFilesBareRepoWorktreeTest {

    @TempDir
    Path baseDir;

    @Spy
    private GitProvider gitProviderSpy;

    @Spy
    @InjectMocks
    private DifferentFiles underTest;

    private Path bareRepo;
    private Path worktree;
    private Path userHome;
    private java.io.File jGitUserHomeBackup;

    @BeforeEach
    void before() throws Exception {
        jGitUserHomeBackup = FS.DETECTED.userHome();
        userHome = Files.createDirectory(baseDir.resolve("userHome"));
        FS.DETECTED.setUserHome(userHome.toFile());

        Path unzipped = Files.createDirectory(baseDir.resolve("unzipped"));
        new UnZipper().act(Paths.get(getClass().getClassLoader().getResource("bare-worktree-template.zip").toURI()), unzipped);
        bareRepo = unzipped.resolve("bare.git").toRealPath();
        worktree = unzipped.resolve("wt").toRealPath();

        // rewrite the absolute links (both are the only thing that depends on the location of the unzipped files)
        Files.write(worktree.resolve(".git"), ("gitdir: " + bareRepo.resolve("worktrees").resolve("wt") + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(bareRepo.resolve("worktrees").resolve("wt").resolve("gitdir"),
                (worktree.resolve(".git") + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void after() {
        FS.DETECTED.setUserHome(jGitUserHomeBackup);
        if (gitProviderSpy != null) {
            gitProviderSpy.close();
        }
    }

    @Test
    void projectRoot() throws Exception {
        assertThat(gitProviderSpy.getProjectRoot(config(worktree, new Properties()))).isEqualTo(worktree);
    }

    @Test
    void currentBranch() throws Exception {
        assertThat(gitProviderSpy.getCurrentBranch(config(worktree, new Properties()))).isEqualTo("feature");
    }

    @Test
    void branchDiff() throws Exception {
        Properties props = new Properties();
        props.setProperty(Property.referenceBranch.prefixedName(), "refs/heads/master");

        Set<Path> result = underTest.get(config(worktree, props));

        assertThat(result).containsExactly(worktree.resolve("child1").resolve("feature-file.txt"));
    }

    @Test
    void branchDiff_fromSubModule() throws Exception {
        Properties props = new Properties();
        props.setProperty(Property.referenceBranch.prefixedName(), "refs/heads/master");

        Set<Path> result = underTest.get(config(worktree.resolve("child2"), props));

        // paths are relative to the work tree, not to the current project
        assertThat(result).containsExactly(worktree.resolve("child1").resolve("feature-file.txt"));
    }

    @Test
    void untrackedAndUncommitted() throws Exception {
        Properties props = new Properties();
        props.setProperty(Property.disableBranchComparison.prefixedName(), "true");
        Path untracked = Files.write(worktree.resolve("child2").resolve("untracked.txt"), "x".getBytes(StandardCharsets.UTF_8));
        Path modified = worktree.resolve("child1").resolve("feature-file.txt");
        Files.write(modified, "modified".getBytes(StandardCharsets.UTF_8));

        Set<Path> result = underTest.get(config(worktree, props));

        assertThat(result).containsExactlyInAnyOrder(untracked, modified);
    }

    private Configuration config(Path workDir, Properties topLevelProjectProperties) throws Exception {
        var session = MavenSessionMock.get(workDir, topLevelProjectProperties);
        session.getCurrentProject().getProperties().putAll(topLevelProjectProperties);
        return new Configuration(session);
    }
}

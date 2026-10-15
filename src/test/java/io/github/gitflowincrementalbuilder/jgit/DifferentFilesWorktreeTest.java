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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.mocks.MavenSessionMock;
import io.github.gitflowincrementalbuilder.mocks.UnZipper;

/**
 * Tests linked worktrees of a <b>bare</b> and of a <b>regular</b> repository, see
 * <a href="https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/issues/1238">#1238</a>.
 * <p>
 * <code>worktree-template.zip</code> contains:
 * <ul>
 * <li><code>bare.git</code>: bare repo with branches <code>master</code> (2 child modules) and <code>feature</code>
 *     (adds <code>child1/feature-file.txt</code>)</li>
 * <li><code>bare-wt</code>: linked worktree of <code>bare.git</code>, checked out at <code>feature</code></li>
 * <li><code>normal</code>: regular repo with the same history, checked out at <code>master</code></li>
 * <li><code>normal-wt</code>: linked worktree of <code>normal</code>, checked out at <code>feature</code></li>
 * </ul>
 * How the zip was created: see <code>worktree-template.README.md</code> next to it in <code>src/test/resources</code>.
 * <p>
 * The worktree links are absolute paths (<code>*-wt/.git</code> and <code>GITDIR/worktrees/*-wt/gitdir</code>), so they
 * are rewritten after unzipping, everything else is untouched.
 */
@ExtendWith(MockitoExtension.class)
public class DifferentFilesWorktreeTest {

    enum Flavor {
        /** Linked worktree of a bare repo. */
        BARE("bare.git", "bare-wt"),
        /** Linked worktree of a regular repo. */
        NORMAL("normal/.git", "normal-wt");

        final String gitDir;
        final String worktreeDir;

        Flavor(String gitDir, String worktreeDir) {
            this.gitDir = gitDir;
            this.worktreeDir = worktreeDir;
        }
    }

    @TempDir
    Path baseDir;

    @Spy
    private GitProvider gitProviderSpy;

    @Spy
    @InjectMocks
    private DifferentFiles underTest;

    private java.io.File jGitUserHomeBackup;

    @BeforeEach
    void before() throws Exception {
        jGitUserHomeBackup = FS.DETECTED.userHome();
        Path userHome = Files.createDirectory(baseDir.resolve("userHome"));
        FS.DETECTED.setUserHome(userHome.toFile());
    }

    /** Unzips the template and rewrites the absolute links of the worktree of the given flavor, returns the worktree path. */
    private Path prepareWorktree(Flavor flavor) throws Exception {
        Path unzipped = Files.createDirectory(baseDir.resolve("unzipped"));
        new UnZipper().act(Paths.get(getClass().getClassLoader().getResource("worktree-template.zip").toURI()), unzipped);
        Path gitDir = unzipped.resolve(flavor.gitDir).toRealPath();
        Path worktree = unzipped.resolve(flavor.worktreeDir).toRealPath();
        Path worktreeGitDir = gitDir.resolve("worktrees").resolve(flavor.worktreeDir);

        Files.write(worktree.resolve(".git"), ("gitdir: " + worktreeGitDir + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(worktreeGitDir.resolve("gitdir"), (worktree.resolve(".git") + "\n").getBytes(StandardCharsets.UTF_8));
        return worktree;
    }

    @AfterEach
    void after() {
        FS.DETECTED.setUserHome(jGitUserHomeBackup);
        if (gitProviderSpy != null) {
            gitProviderSpy.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void projectRoot(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
        assertThat(gitProviderSpy.getProjectRoot(config(worktree, new Properties()))).isEqualTo(worktree);
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void projectRoot_fromSubModule(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
        assertThat(gitProviderSpy.getProjectRoot(config(worktree.resolve("child2"), new Properties()))).isEqualTo(worktree);
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void currentBranch(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
        assertThat(gitProviderSpy.getCurrentBranch(config(worktree, new Properties()))).isEqualTo("feature");
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void branchDiff(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
        Properties props = new Properties();
        props.setProperty(Property.referenceBranch.prefixedName(), "refs/heads/master");

        Set<Path> result = underTest.get(config(worktree, props));

        assertThat(result).containsExactly(worktree.resolve("child1").resolve("feature-file.txt"));
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void branchDiff_fromSubModule(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
        Properties props = new Properties();
        props.setProperty(Property.referenceBranch.prefixedName(), "refs/heads/master");

        Set<Path> result = underTest.get(config(worktree.resolve("child2"), props));

        // paths are relative to the work tree, not to the current project
        assertThat(result).containsExactly(worktree.resolve("child1").resolve("feature-file.txt"));
    }

    @ParameterizedTest
    @EnumSource(Flavor.class)
    void untrackedAndUncommitted(Flavor flavor) throws Exception {
        Path worktree = prepareWorktree(flavor);
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
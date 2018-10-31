package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.ProcessUtils;
import com.vackosar.gitflowincrementalbuild.control.Property;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}.
 * <p/>
 * This test is expected to be called via {@code maven-failsafe-plugin} and requires two system properties:
 * <ul>
 * <li>{@code gib.it.repo} defining the path of the maven repo containing the {@code gitflow-incremental-builder} jarfile to test</li>
 * <li>{@code gib.it.version} defining the version of the {@code gitflow-incremental-builder} jarfile to test</li>
 * </ul>
 */
public class MavenIntegrationTest extends BaseRepoTest {

    private static final String DEFAULT_POMFILE_ARG = "--file=parent/pom.xml";

    private static String localRepoArg;
    private static String gibVersionArg;
    private static boolean initialInstallDone;

    @BeforeClass
    public static void evaluateSystemProperties() throws IOException, InterruptedException {
        localRepoArg = "-Dmaven.repo.local=" + System.getProperty("gib.it.repo");
        gibVersionArg = "-DgibVersion=" + System.getProperty("gib.it.version");

        System.out.println(
                "The first test method will execute an initial 'mvn install ...' on the test project to populate the test repo."
                + " This might take a while.");
    }

    /**
     * Installs all test artifacts/modules to avoid dependency problems when only building a subset incrementally.
     * <p/>
     * This also downloads all required maven core and plugin dependencies into the test repo.
     * <p/>
     * This is performed only once for the entire class but cannot be moved to {@link BeforeClass} as {@link BaseRepoTest} (re-)creates the
     * test project for each test in {@link Before}.
     *
     * @throws IOException on process execution errors
     * @throws InterruptedException on process execution errors
     */
    @Before
    public void initialInstall() throws IOException, InterruptedException {
        if (initialInstallDone) {
            return;
        }
        ProcessUtils.awaitProcess(new ProcessBuilder(
                ProcessUtils.cmdArgs(
                        "mvn", "install", localRepoArg, gibVersionArg, DEFAULT_POMFILE_ARG, "-Dgib." + Property.enabled + "=false"))
                .directory(getLocalRepoMock().getBaseCanonicalBaseFolder())
                .start());
        initialInstallDone = true;
    }
    
    @Test
    public void worktreeFails() throws Exception {
        final String output = executeBuild(Collections.singletonList("--file=wrkf2/parent/pom.xml"));
        System.out.println(output);
        Assert.assertTrue(output.contains(GuiceModule.UNSUPPORTED_WORKTREE));
    }

    @Test
    public void logChanges() throws Exception {
        final String output = executeBuild(Collections.singletonList("-X"));
        System.out.println(output);
        Assert.assertTrue(output.contains("[DEBUG] Changed file: "));
    }

    @Test
    public void buildAllSkipTest() throws Exception {
        final String output = executeBuild(Arrays.asList(
                "-Dgib." + Property.buildAll + "=true",
                "-Dgib." + Property.skipTestsForNotImpactedModules.name() + "=true")
        );
        System.out.println(output);

        Assert.assertTrue(output.contains(" child1"));
        Assert.assertTrue(output.contains(" child2"));
        Assert.assertTrue(output.contains(" subchild1"));
        Assert.assertTrue(output.contains(" subchild42"));
        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertTrue(output.contains("[INFO] Tests are skipped."));
    }
    
    @Test
    public void buildWithAlsoMake() throws Exception {
        final String output = executeBuild(Collections.singletonList("-am"));
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
    }

    @Test
    public void buildNoChanged() throws Exception {
        Git git = localRepoMock.getGit();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        git.checkout().setName("develop").call();
        final String output = executeBuild(Collections.singletonList("-Dgib." + Property.baseBranch.name() + "=refs/heads/develop"));
        Assert.assertTrue(output.contains("Executing validate goal on current project only."));
        Assert.assertTrue(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" subchild2"));
        Assert.assertFalse(output.contains(" child3"));
        Assert.assertFalse(output.contains(" child4"));
        Assert.assertFalse(output.contains(" subchild41"));
        Assert.assertFalse(output.contains(" child6"));
    }


    @Test
    public void buildWithAlsoMakeSkip() throws Exception {
        final String output = executeBuild(Arrays.asList(
                "-am",
                "-Dgib." + Property.skipTestsForNotImpactedModules.name() + "=true")
        );
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertTrue(output.contains("[INFO] Tests are skipped."));
    }

    @Test
    public void buildWithoutAlsoMake() throws Exception {
        final String output = executeBuild(Collections.emptyList());
        System.out.println(output);

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" child6"));

        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
    }

    private String executeBuild(List<String> args) throws IOException, InterruptedException {
        final List<String> commandBase = ProcessUtils.cmdArgs("mvn", "package", localRepoArg, gibVersionArg);
        final List<String> commandBaseWithFile;
        if (args.stream().noneMatch(s->s.startsWith("--file"))) {
            commandBaseWithFile = Stream.concat(commandBase.stream(), Stream.of(DEFAULT_POMFILE_ARG)).collect(Collectors.toList());
        } else {
            commandBaseWithFile = commandBase;
        }
        List<String> command = Stream.concat(commandBaseWithFile.stream(), args.stream()).collect(Collectors.toList());
        final Process process =
                new ProcessBuilder(command)
                        .directory(getLocalRepoMock().getBaseCanonicalBaseFolder())
                        .start();
        return ProcessUtils.awaitProcess(process);
    }
}

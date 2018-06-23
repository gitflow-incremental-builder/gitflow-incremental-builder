package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.control.Property;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integration test which installs current version into your Maven Repository! See {@link MavenInstallRunningIntegrationTest#installCurrentVersion()}
 */
public class MavenInstallRunningIntegrationTest extends BaseRepoTest {

    private static final String DEFAULT_POMFILE_ARG = "--file=parent/pom.xml";

    private static String gibVersionArg;

    @BeforeClass
    public static void installCurrentVersion() throws IOException, InterruptedException {
        awaitProcess(new ProcessBuilder(cmdArgs("mvn", "install", "-Dmaven.test.skip=true")).start());
        // Get current version from pom.xml
        String version = Files.readAllLines(Paths.get("pom.xml")).stream().filter(s -> s.contains("<version>")).findFirst().get()
            .replaceAll("</*version>", "").replaceAll("^[ \t]*", "");
        gibVersionArg = "-DgibVersion=" + version;
    }

    /**
     * Installs all test artifacts/modules to avoid dependency problems when only building a subset incrementally. 
     *
     * @throws IOException on process execution errors
     * @throws InterruptedException on process execution errors
     */
    @Before
    public void installTestArtifacts() throws IOException, InterruptedException {
        awaitProcess(
            new ProcessBuilder(cmdArgs("mvn", "install",
                DEFAULT_POMFILE_ARG, gibVersionArg, "-Dgib." + Property.enabled + "=false"))
                    .directory(getLocalRepoMock().getBaseCanonicalBaseFolder())
                    .start());
    }

    /**
     * Removes the test artifacts/modules which have been installed by {@link #installTestArtifacts()}.
     *
     * @throws IOException on process execution errors
     * @throws InterruptedException on process execution errors
     */
    @After
    public void purgeInstalledTestArtifacts() throws IOException, InterruptedException {
        awaitProcess(
            new ProcessBuilder(cmdArgs("mvn", "org.codehaus.mojo:build-helper-maven-plugin:3.0.0:remove-project-artifact",
                DEFAULT_POMFILE_ARG, gibVersionArg,"-Dgib." + Property.enabled + "=false"))
                    .directory(getLocalRepoMock().getBaseCanonicalBaseFolder())
                    .start());
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
        final List<String> commandBase = cmdArgs("mvn", "package", gibVersionArg);
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
        return awaitProcess(process);
    }

    private static String awaitProcess(Process process) throws InterruptedException {
        final String stdOut = convertStreamToString(process.getInputStream());
        final String stdErr = convertStreamToString(process.getErrorStream());
        final int returnCode = process.waitFor();
        if (returnCode > 0) {
            System.err.println(stdOut);
            System.err.println(stdErr);
            Assert.fail("Process failed with return code " + returnCode);
        }
        return stdOut;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static List<String> cmdArgs(String... args) {
        return SystemUtils.IS_OS_WINDOWS
            ? Stream.concat(Stream.of("cmd", "/c"), Arrays.stream(args)).collect(Collectors.toList())
            : Arrays.asList(args);
    }
}

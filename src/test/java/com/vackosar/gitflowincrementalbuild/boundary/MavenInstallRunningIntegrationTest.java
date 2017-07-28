package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.control.Property;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.Assert;
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

    @BeforeClass
    public static void installCurrentVersion() throws IOException {
        new ProcessBuilder("mvn", "clean", "install", "-skipTest").start();
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
        System.out.println(output);

        Assert.assertTrue(output.contains("Executing validate goal only."));
        Assert.assertTrue(output.contains(" child1"));
        Assert.assertTrue(output.contains(" child2"));
        Assert.assertTrue(output.contains(" subchild1"));
        Assert.assertTrue(output.contains(" subchild42"));
        Assert.assertTrue(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertTrue(output.contains(" child4"));
        Assert.assertTrue(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
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
        String version = Files.readAllLines(Paths.get("pom.xml")).stream().filter(s -> s.contains("<version>")).findFirst().get().replaceAll("</*version>", "").replaceAll("^[ \t]*", "");
        final List<String> commandBase = Arrays.asList(
//                "cmd", "/c" // Uncomment for Windows.
                "mvn",
                "package",
                "-DgibVersion=" + version);
        final List<String> commandBaseWithFile;
        if (args.stream().noneMatch(s->s.startsWith("--file"))) {
            commandBaseWithFile = Stream.concat(commandBase.stream(), Stream.of("--file=parent/pom.xml")).collect(Collectors.toList());
        } else {
            commandBaseWithFile = commandBase;
        }
        List<String> command = Stream.concat(commandBaseWithFile.stream(), args.stream()).collect(Collectors.toList());
        final Process process =
                new ProcessBuilder(command)
                        .directory(getLocalRepoMock().getBaseCanonicalBaseFolder())
                        .start();
        String output = convertStreamToString(process.getInputStream());
        System.out.println(convertStreamToString(process.getErrorStream()));
        process.waitFor();
        return output;
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

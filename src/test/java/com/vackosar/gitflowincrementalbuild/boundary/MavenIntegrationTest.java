package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.BaseRepoTest;
import com.vackosar.gitflowincrementalbuild.ProcessUtils;
import com.vackosar.gitflowincrementalbuild.control.DifferentFiles;
import com.vackosar.gitflowincrementalbuild.control.Property;

import org.apache.commons.lang3.Validate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}.
 * <p/>
 * This test is expected to be called via {@code maven-failsafe-plugin} and requires two system properties:
 * <ul>
 * <li>{@code gibIntegrationTestRepo} defining the path of the maven repo containing the {@code gitflow-incremental-builder} jarfile to
 * test</li>
 * <li>{@code gibIntegrationTestVersion} defining the version of the {@code gitflow-incremental-builder} jarfile to test</li>
 * </ul>
 */
public class MavenIntegrationTest extends BaseRepoTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenIntegrationTest.class);

    private static final String DEFAULT_POMFILE_ARG = "--file=parent/pom.xml";

    private static String localRepoArg;
    private static String gibVersion;
    private static String gibVersionArg;
    private static boolean initialInstallDone;

    @Rule
    public final TestName testNameRule = new TestName();

    @BeforeClass
    public static void evaluateSystemProperties() throws IOException, InterruptedException {
        localRepoArg = "-Dmaven.repo.local="
                + Validate.notEmpty(System.getProperty("gibIntegrationTestRepo"), "gibIntegrationTestRepo not set");
        gibVersion = Validate.notEmpty(System.getProperty("gibIntegrationTestVersion"), "gibIntegrationTestVersion not set");
        gibVersionArg = "-DgibVersion=" + gibVersion;

        LOGGER.info("The first test method will execute an initial 'mvn install ...' on the test project to populate the test repo."
                + " This might take a while.");
        LOGGER.info("Arguments: {}, {}", gibVersionArg, localRepoArg);
    }

    /**
     * Installs all test artifacts/modules to avoid dependency problems when only building a subset incrementally.
     * <p/>
     * This might download all required maven core and plugin dependencies into the test repo, but test setup in pom.xml tries to prevent that.
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
        ProcessUtils.startAndWaitForProcess(
                Arrays.asList("mvn", "install", localRepoArg, gibVersionArg, DEFAULT_POMFILE_ARG, prop(Property.enabled, "false")),
                localRepoMock.getBaseCanonicalBaseFolder());
        initialInstallDone = true;
    }

    @Test
    public void logVersion() throws IOException, InterruptedException {
        final String output = executeBuild("-N");
        Assert.assertTrue(output.contains("gitflow-incremental-builder " + gibVersion + " starting..."));
    }

    @Test
    public void worktreeFails() throws Exception {
        final String output = executeBuild("--file=wrkf2/parent/pom.xml");
        Assert.assertTrue(output.contains(DifferentFiles.UNSUPPORTED_WORKTREE));
    }

    @Test
    public void logChanges() throws Exception {
        final String output = executeBuild("-X", "-N");
        Assert.assertTrue(output.contains("[WARNING] Ignoring changed file in non-reactor module: "));
    }

    @Test
    public void buildAllSkipTest() throws Exception {
        final String output = executeBuild(prop(Property.buildAll, "true"), prop(Property.skipTestsForUpstreamModules, "true"));

        assertBuilAllSkipTest(output);
    }

    @Test
    public void buildAllSkipTest_emptyPropertyValues() throws Exception {
        final String output = executeBuild(prop(Property.buildAll, ""), prop(Property.skipTestsForUpstreamModules, ""));

        assertBuilAllSkipTest(output);
    }

    private static void assertBuilAllSkipTest(final String output) {
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
        final String output = executeBuild("-am");

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
        checkoutDevelop();

        final String output = executeBuild(prop(Property.baseBranch, "refs/heads/develop"));

        Assert.assertTrue(output.contains("Executing validate goal on current project only"));
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
        final String output = executeBuild("-am", prop(Property.skipTestsForUpstreamModules, "true"));

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
        final String output = executeBuild();

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

    @Test
    public void buildWithAlsoMakeDependents() throws Exception {
        final String output = executeBuild("-amd", prop(Property.buildDownstream, "derived"));

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

    @Test
    public void buildWithSingleSelectedModule() throws Exception {
        checkoutDevelop();

        workAroundMissingParents();

        final String output = executeBuild("-pl", "child2", prop(Property.disableBranchComparison, "true"));

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertTrue(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" subchild2"));
        Assert.assertFalse(output.contains(" child3"));
        Assert.assertFalse(output.contains(" child4"));
        Assert.assertFalse(output.contains(" subchild41"));
        Assert.assertFalse(output.contains(" child6"));
        Assert.assertFalse(output.contains(" testJarDependency"));
        Assert.assertFalse(output.contains(" testJarDependent"));

        Assert.assertTrue(output.contains("Building explicitly selected projects"));
    }

    @Test
    public void buildWithSingleLeafModule() throws Exception {
        checkoutDevelop();

        workAroundMissingParents();

        final String output = executeBuild("-f", "parent/child3", prop(Property.disableBranchComparison, "true"));

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertFalse(output.contains(" child4"));
        Assert.assertFalse(output.contains(" subchild41"));
        Assert.assertFalse(output.contains(" child6"));
        Assert.assertFalse(output.contains(" testJarDependency"));
        Assert.assertFalse(output.contains(" testJarDependent"));

        Assert.assertTrue(output.contains("Building single project"));
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMake() throws Exception {
        checkoutDevelop();

        workAroundMissingParents();

        // tests that child6 upstream of child3 is built
        Files.write(repoPath.resolve("parent").resolve("child6").resolve("changed.xml"), new byte[0]);
        // tests that child1 (that is _not_ in MavenSession.projects but in .allProjects) is _not_ resolved to parent (the root)
        Files.write(repoPath.resolve("parent").resolve("child1").resolve("changed.xml"), new byte[0]);

        final String output = executeBuild("-pl", "child3", "-am", prop(Property.disableBranchComparison, "true"));

        Assert.assertFalse(output.contains("Building child1")); // "Building" prefix is required because child1 will be listed as changed
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertFalse(output.contains(" child4"));
        Assert.assertFalse(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertFalse(output.contains(" testJarDependency"));
        Assert.assertFalse(output.contains(" testJarDependent"));
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMakeDependends() throws Exception {
        checkoutDevelop();

        workAroundMissingParents();

        final String output = executeBuild("-pl", "child6", "-amd", prop(Property.disableBranchComparison, "true"));

        Assert.assertFalse(output.contains(" child1"));
        Assert.assertFalse(output.contains(" child2"));
        Assert.assertFalse(output.contains(" subchild1"));
        Assert.assertFalse(output.contains(" subchild42"));
        Assert.assertFalse(output.contains(" subchild2"));
        Assert.assertTrue(output.contains(" child3"));
        Assert.assertFalse(output.contains(" child4"));
        Assert.assertFalse(output.contains(" subchild41"));
        Assert.assertTrue(output.contains(" child6"));
        Assert.assertFalse(output.contains(" testJarDependency"));
        Assert.assertFalse(output.contains(" testJarDependent"));
    }

    private void checkoutDevelop() throws GitAPIException, CheckoutConflictException, RefAlreadyExistsException,
            RefNotFoundException, InvalidRefNameException {
        Git git = localRepoMock.getGit();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        git.checkout().setName("develop").call();
    }

    /**
     * GIB is not active for any of the submodules in the test git repo because they do not reference the root project as parent.
     * This method works around this by registering GIB via {@code .mvn/extensions.xml}.
     *
     * @see <a href="https://maven.apache.org/examples/maven-3-lifecycle-extensions.html">Using Maven 3 lifecycle extension</a>
     */
    private void workAroundMissingParents() throws IOException {
        String extensionXml =
                "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "  xsi:schemaLocation=\"http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd\">\n" +
                "  <extension>\n" +
                "    <groupId>com.vackosar.gitflowincrementalbuilder</groupId>\n" +
                "    <artifactId>gitflow-incremental-builder</artifactId>\n" +
                "    <version>" + gibVersion + "</version>\n" +
                "  </extension>\n" +
                "</extensions>";
        Path extensionsPath = Files.createDirectory(repoPath.resolve(".mvn")).resolve("extensions.xml");
        Files.write(extensionsPath, extensionXml.getBytes(StandardCharsets.UTF_8));
    }

    private String executeBuild(String... args) throws IOException, InterruptedException {
        final List<String> commandBase = Arrays.asList("mvn", "-e", "package", localRepoArg, gibVersionArg);
        final List<String> commandBaseWithFile;
        if (Arrays.stream(args).noneMatch(s -> s.startsWith("--file") || s.equals("-f"))) {
            commandBaseWithFile = Stream.concat(commandBase.stream(), Stream.of(DEFAULT_POMFILE_ARG)).collect(Collectors.toList());
        } else {
            commandBaseWithFile = commandBase;
        }
        List<String> command = Stream.concat(commandBaseWithFile.stream(), Arrays.stream(args)).collect(Collectors.toList());
        String output = ProcessUtils.startAndWaitForProcess(command, localRepoMock.getBaseCanonicalBaseFolder());
        LOGGER.info("Output of {}({}):\n{}", testNameRule.getMethodName(), String.join(" ", command), output);
        return output;
    }

    private static String prop(Property property, String value) {
        String propString =  "-D" + property.fullName();
        if (value != null && !value.isEmpty()) {
            propString += "=" + value;
        }
        return propString;
    }
}

package com.vackosar.gitflowincrementalbuild.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integration test base running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}.
 * <p/>
 * Tests extending this base class are expected to be called via {@code maven-failsafe-plugin}, dependends on {@code settings-it.xml} and requires two system
 * properties:
 * <ul>
 * <li>{@code settings.localRepository} defining the path to the regular local Maven repo (containing the all the basic dependencies and plugins)
 * test</li>
 * <li>{@code project.version} defining the current project version</li>
 * </ul>
 * Furthermore, {@code mvn} must be on the {@code PATH} environment variable and {@code JAVA_HOME} must also be set.
 */
public abstract class MavenIntegrationTestBase extends BaseRepoTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenIntegrationTestBase.class);

    private static final Set<Class<?>> INITIAL_INSTALL_DONE = new HashSet<>();

    private static final String DEFAULT_POMFILE_ARG = "--file=parent/pom.xml";

    private static final Pattern LOG_LINE_FILTER_PATTERN = Pattern.compile("^\\[.*INFO.*\\] Download(ing|ed) from local.central: .*");

    protected static String gibVersion;

    private static List<String> defaultArgs;

    private String testDisplayName;

    @BeforeAll
    static void evaluateSystemProperties() throws IOException, InterruptedException, URISyntaxException {
        gibVersion = Validate.notEmpty(System.getProperty("project.version"), "project.version not set");

        defaultArgs = Collections.unmodifiableList(Arrays.asList(
                "--settings=" + createPathToSettingsXml(),
                "-DgibIntegrationTestRepoRemote=" + createUrlToRemoteRepo(),    // used in/required by settings-it.xml
                "--batch-mode",
                "-Dstyle.color=always"));

        LOGGER.info("The first test method will execute an initial 'mvn install ...' on the test project to populate the test repo."
                + " This might take a while.");
        LOGGER.info("Default arguments: {}", defaultArgs);
    }

    private static String createPathToSettingsXml() throws URISyntaxException {
        URL settingsUrl = Validate.notNull(
                Thread.currentThread().getContextClassLoader().getResource("settings-it.xml"), "settings-it.xml not found on classpath");
        return Paths.get(settingsUrl.toURI()).toAbsolutePath().toString();
    }

    private static String createUrlToRemoteRepo() throws MalformedURLException {
        String regularLocalRepo = Validate.notEmpty(System.getProperty("settings.localRepository"), "settings.localRepository not set");
        return Paths.get(regularLocalRepo).toUri().toURL().toString();
    }

    /**
     * Installs all test artifacts/modules to avoid dependency problems when only building a subset incrementally.
     * <p/>
     * This will download all required maven core and plugin dependencies into the test repo (from the regular local repo, not from the internet).
     * <p/>
     * This is performed only once for the entire class but cannot be moved to {@link BeforeAll} as {@link BaseRepoTest} (re-)creates the
     * test project for each test in {@link BeforeEach}.
     *
     * @throws IOException on process execution errors
     * @throws InterruptedException on process execution errors
     * @throws URISyntaxException on classptah lookup problems
     */
    @BeforeEach
    void initialInstall(TestInfo testInfo) throws IOException, InterruptedException, URISyntaxException {
        testDisplayName = testInfo.getDisplayName();
        Class<?> testClass = testInfo.getTestClass().get();
        if (INITIAL_INSTALL_DONE.contains(testClass)) {
            return;
        }
        copyCommonBuildParentPom();
        String disableGib = prop(Property.disable, "true");
        executeBuild(true, false, "--file=build-parent/pom-common.xml", disableGib);
        executeBuild(true, false, "--file=build-parent/pom.xml", disableGib);
        executeBuild(true, false, DEFAULT_POMFILE_ARG, disableGib);
        INITIAL_INSTALL_DONE.add(testClass);
    }

    private void copyCommonBuildParentPom() throws URISyntaxException, IOException {
        String relativePath = "build-parent/pom-common.xml";
        URL buildParentURL = Validate.notNull(
                Thread.currentThread().getContextClassLoader().getResource(relativePath),"%s not found on classpath", relativePath);
        Path source = Paths.get(buildParentURL.toURI()).toAbsolutePath();
        Path target = localRepoMock.getBaseCanonicalBaseFolder().toPath().resolve(relativePath);
        Files.copy(source, target);
    }

    @Test
    public void logVersion() throws IOException, InterruptedException {
        final String output = executeBuild("-N");
        assertThat(output).contains("gitflow-incremental-builder " + gibVersion + " starting...");
    }

    @Test
    public void worktreeFails() throws Exception {
        final String output = executeBuild("--file=wrkf2/parent/pom.xml");
        assertThat(output).contains(DifferentFiles.UNSUPPORTED_WORKTREE);
    }

    @Test
    public void nonRecursive() throws Exception {
        final String output = executeBuild("-N");
        assertThat(output).contains("Building single project (without any adjustment): parent");
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
        assertThat(output).contains(" child1")
                .contains(" child2")
                .contains(" subchild1")
                .contains(" subchild42")
                .contains(" subchild2")
                .contains(" child3")
                .contains(" child4")
                .contains(" subchild41")
                .contains(" child6")
                .contains(" Tests are skipped.");
    }

    @Test
    public void buildWithAlsoMake() throws Exception {
        final String output = executeBuild("-am");

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .contains(" subchild2")
                .contains(" child3")
                .contains(" child4")
                .contains(" subchild41")
                .contains(" child6");
    }

    @Test
    public void buildNoChanged() throws Exception {
        checkoutDevelop();

        final String output = executeBuild(prop(Property.baseBranch, "refs/heads/develop"));

        assertThat(output).contains("Executing validate goal on current project only")
                .contains(" parent")
                .doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" subchild2")
                .doesNotContain(" child3")
                .doesNotContain(" child4")
                .doesNotContain(" subchild41")
                .doesNotContain(" child6");
    }


    @Test
    public void buildWithAlsoMakeSkip() throws Exception {
        final String output = executeBuild("-am", prop(Property.skipTestsForUpstreamModules, "true"));

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .contains(" subchild2")
                .contains(" child3")
                .contains(" child4")
                .contains(" subchild41")
                .contains(" child6")
                .contains(" Tests are skipped.");
    }

    @Test
    public void buildWithoutAlsoMake() throws Exception {
        final String output = executeBuild();

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" child6")
                .contains(" subchild2")
                .contains(" child3")
                .contains(" child4")
                .contains(" subchild41");
    }

    @Test
    public void buildWithAlsoMakeDependents() throws Exception {
        final String output = executeBuild("-amd", prop(Property.buildDownstream, "derived"));

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" child6")
                .contains(" subchild2")
                .contains(" child3")
                .contains(" child4")
                .contains(" subchild41");
    }

    @Test
    public void buildWithSingleSelectedModule() throws Exception {
        checkoutDevelop();

        final String output = executeBuild("-pl", "child2", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain(" child1")
                .contains(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" subchild2")
                .doesNotContain(" child3")
                .doesNotContain(" child4")
                .doesNotContain(" subchild41")
                .doesNotContain(" child6")
                .doesNotContain(" testJarDependency")
                .doesNotContain(" testJarDependent")
                .contains("Building explicitly selected projects");
    }

    @Test
    public void buildWithSingleLeafModule() throws Exception {
        checkoutDevelop();

        final String output = executeBuild("-f", "parent/child3", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" subchild2")
                .contains(" child3")
                .doesNotContain(" child4")
                .doesNotContain(" subchild41")
                .doesNotContain(" child6")
                .doesNotContain(" testJarDependency")
                .doesNotContain(" testJarDependent")
                .contains("Building single project");
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMake() throws Exception {
        checkoutDevelop();

        // tests that child6 upstream of child3 is built
        Files.write(repoPath.resolve("parent").resolve("child6").resolve("changed.xml"), new byte[0]);
        // tests that child1 (that is _not_ in MavenSession.projects but in .allProjects) is _not_ resolved to parent (the root)
        Files.write(repoPath.resolve("parent").resolve("child1").resolve("changed.xml"), new byte[0]);

        final String output = executeBuild("-pl", "child3", "-am", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1") // "Building" prefix is required because child1 will be listed as changed
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" subchild2")
                .contains(" child3")
                .doesNotContain(" child4")
                .doesNotContain(" subchild41")
                .contains(" child6")
                .doesNotContain(" testJarDependency")
                .doesNotContain(" testJarDependent");
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMakeDependends() throws Exception {
        checkoutDevelop();

        final String output = executeBuild("-pl", "child6", "-amd", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain(" child1")
                .doesNotContain(" child2")
                .doesNotContain(" subchild1")
                .doesNotContain(" subchild42")
                .doesNotContain(" subchild2")
                .contains(" child3")
                .doesNotContain(" child4")
                .doesNotContain(" subchild41")
                .contains(" child6")
                .doesNotContain(" testJarDependency")
                .doesNotContain(" testJarDependent");
    }

    private void checkoutDevelop() throws GitAPIException, CheckoutConflictException, RefAlreadyExistsException,
            RefNotFoundException, InvalidRefNameException {
        Git git = localRepoMock.getGit();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        git.checkout().setName("develop").call();
    }

    protected String executeBuild(String... args) throws IOException, InterruptedException {
        return executeBuild(false, true, args);
    }

    protected String executeBuild(boolean installInsteadOfPackage, boolean logOutput, String... args) throws IOException, InterruptedException {
        final List<String> commandBase = new ArrayList<>(Arrays.asList("mvn", "-e"));
        if (installInsteadOfPackage) {
            commandBase.add("install");
        } else if (args.length == 0 || !args[0].startsWith("help:")) {
            commandBase.add("package");
        }
        final List<String> commandBaseWithFile;
        if (Arrays.stream(args).noneMatch(s -> s.startsWith("--file") || s.equals("-f"))) {
            commandBaseWithFile = Stream.concat(commandBase.stream(), Stream.of(DEFAULT_POMFILE_ARG)).collect(Collectors.toList());
        } else {
            commandBaseWithFile = commandBase;
        }
        // commandBaseWithFile + args + defaultArgs
        List<String> command = Stream.concat(commandBaseWithFile.stream(), Stream.concat(Arrays.stream(args), defaultArgs.stream()))
                .collect(Collectors.toList());
        String output = ProcessUtils.startAndWaitForProcess(command, localRepoMock.getBaseCanonicalBaseFolder(),
                line -> !LOG_LINE_FILTER_PATTERN.matcher(line).matches());
        if (logOutput) {
            LOGGER.info("Output of {}({}):\n{}", testDisplayName, String.join(" ", command), output);
        }
        return output;
    }

    protected static String prop(Property property, String value) {
        String propString =  "-D" + property.prefixedName();
        if (value != null && !value.isEmpty()) {
            propString += "=" + value;
        }
        return propString;
    }
}

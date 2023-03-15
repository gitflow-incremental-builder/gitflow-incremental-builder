package io.github.gitflowincrementalbuilder.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import io.github.gitflowincrementalbuilder.BaseRepoTest;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.util.ProcessUtils;

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

    private static final Set<String> INITIAL_INSTALL_DONE = new HashSet<>();

    private static final String DEFAULT_POMFILE_ARG = "--file=parent/pom.xml";
    private static final String QUARKUS_POMFILE_ARG = "--file=quarkus-scenario/pom.xml";

    private static final Pattern LOG_LINE_FILTER_PATTERN = Pattern.compile("^\\[.*INFO.*\\] Download(ing|ed) from local.central: .*");

    private static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    protected static String gibVersion;

    private static List<String> defaultArgs;

    private String testDisplayName;

    protected String latestBuildOutput;

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
    void initialInstall(TestInfo testInfo) throws Exception {
        testDisplayName = testInfo.getDisplayName();
        String disableGib = prop(Property.disable, "true");
        Class<?> testClass = testInfo.getTestClass().get();
        String cacheKey = testClass.getName();
        if (!INITIAL_INSTALL_DONE.contains(cacheKey)) {
            copyCommonBuildParentPom();
            executeBuild(true, false, "--file=build-parent/pom-common.xml", disableGib);
            executeBuild(true, false, "--file=build-parent/pom.xml", disableGib);
            executeBuild(true, false, DEFAULT_POMFILE_ARG, disableGib);
            INITIAL_INSTALL_DONE.add(cacheKey);
        }
        if (testDisplayName.startsWith("quarkusScenario_")) {
            checkout(Branch.QUARKUS);
            cacheKey = cacheKey + "_quarkusScenario";
            if (!INITIAL_INSTALL_DONE.contains(cacheKey)) {
                executeBuild(true, false, QUARKUS_POMFILE_ARG, disableGib);
                INITIAL_INSTALL_DONE.add(cacheKey);
            }
        }
    }

    private void copyCommonBuildParentPom() throws URISyntaxException, IOException {
        String relativePath = "build-parent/pom-common.xml";
        URL buildParentURL = Validate.notNull(
                Thread.currentThread().getContextClassLoader().getResource(relativePath),"%s not found on classpath", relativePath);
        Path source = Paths.get(buildParentURL.toURI()).toAbsolutePath();
        Path target = localRepoMock.getRepoDir().resolve(relativePath);
        Files.copy(source, target);
    }

    @Test
    public void logVersion() throws IOException, InterruptedException {
        final String output = executeBuild("-N");
        assertThat(output).contains("gitflow-incremental-builder " + gibVersion + " starting...");
        assertThat(output).doesNotContain("was not tested with gitflow-incremental-builder");
    }

    @Test
    public void worktreeFails() throws Exception {
        final String output = executeBuild("--file=wrkf2/parent/pom.xml");
        assertThat(output).contains(UNSUPPORTED_WORKTREE);
    }

    @Test
    public void nonRecursive() throws Exception {
        final String output = executeBuild("-N");
        assertThat(output).contains("Building single project (without any adjustment): parent");
    }

    @Test
    public void buildAllSkipTest() throws Exception {
        final String output = executeBuild(prop(Property.buildAll, "true"), prop(Property.skipTestsForUpstreamModules, "true"));

        assertBuildAllSkipTest(output);
    }

    @Test
    public void buildAllSkipTest_emptyPropertyValues() throws Exception {
        final String output = executeBuild(prop(Property.buildAll, ""), prop(Property.skipTestsForUpstreamModules, ""));

        assertBuildAllSkipTest(output);
    }

    private static void assertBuildAllSkipTest(final String output) {
        assertThat(output).contains("Building child1")
                .contains("Building child2")
                .contains("Building subchild1")
                .contains("Building subchild42")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41")
                .contains("Building child6")
                .contains(" Tests are skipped.");
    }

    @Test
    public void buildWithAlsoMake() throws Exception {
        final String output = executeBuild("-am");

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41")
                .contains("Building child6");
    }

    @Test
    public void buildNoChanged() throws Exception {
        checkout(Branch.DEVELOP);

        final String output = executeBuild(prop(Property.baseBranch, "refs/heads/develop"));

        assertThat(output).contains("Executing validate goal on current project only")
                .contains("Building parent")
                .doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .doesNotContain("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .doesNotContain("Building child6");
    }


    @Test
    public void buildWithAlsoMakeSkip() throws Exception {
        final String output = executeBuild("-am", prop(Property.skipTestsForUpstreamModules, "true"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41")
                .contains("Building child6")
                .contains(" Tests are skipped.");
    }

    @Test
    public void buildWithoutAlsoMake() throws Exception {
        final String output = executeBuild();

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building child6")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41");
    }

    @Test
    public void buildWithAlsoMakeDependents() throws Exception {
        final String output = executeBuild("-amd", prop(Property.buildDownstream, "derived"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building child6")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41");
    }

    @Test
    public void buildWithSingleSelectedModule() throws Exception {
        checkout(Branch.DEVELOP);

        final String output = executeBuild("-pl", "child2", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1")
                .contains("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .doesNotContain("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .doesNotContain("Building child6")
                .doesNotContain("Building testJarDependency")
                .doesNotContain("Building testJarDependent")
                .contains("Building explicitly selected projects");
    }

    @Test
    public void buildWithSingleLeafModule() throws Exception {
        checkout(Branch.DEVELOP);

        final String output = executeBuild("-f", "parent/child3", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .contains("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .doesNotContain("Building child6")
                .doesNotContain("Building testJarDependency")
                .doesNotContain("Building testJarDependent")
                .contains("Building single project");
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMake() throws Exception {
        checkout(Branch.DEVELOP);

        // tests that child6 upstream of child3 is built
        Files.write(repoPath.resolve("parent").resolve("child6").resolve("changed.xml"), new byte[0]);
        // tests that child1 (that is _not_ in MavenSession.projects but in .allProjects) is _not_ resolved to parent (the root)
        Files.write(repoPath.resolve("parent").resolve("child1").resolve("changed.xml"), new byte[0]);

        final String output = executeBuild("-pl", "child3", "-am", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1") // "Building" prefix is required because child1 will be listed as changed
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .contains("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .contains("Building child6")
                .doesNotContain("Building testJarDependency")
                .doesNotContain("Building testJarDependent");
    }

    @Test
    public void buildWithSingleSelectedModule_alsoMakeDependends() throws Exception {
        checkout(Branch.DEVELOP);

        final String output = executeBuild("-pl", "child6", "-amd", prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .contains("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .contains("Building child6")
                .doesNotContain("Building testJarDependency")
                .doesNotContain("Building testJarDependent");
    }

    @Test
    public void buildWithSingleSelectedModule_dsph_transitiveParentChanged() throws Exception {

        // Child modules of 'parent' (e.g. 'child4') unfortunately don't use 'parent' as their <parent>,
        // so we need to adjust that for this case (a _transitive_ parent must be changed and build-parent is not known to the git repo).
        var child4PomPath = repoPath.resolve("parent").resolve("child4").resolve("pom.xml");
        var child4PomString = Files.readString(child4PomPath);
        child4PomString = child4PomString.replaceAll("build-parent", "parent");
        child4PomString = child4PomString.replaceFirst("../../parent/pom.xml", "../pom.xml");
        Files.writeString(child4PomPath, child4PomString);
        Git git = localRepoMock.getGit();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Switch child4 parent from 'build-parent' to 'parent'").call();

        Files.write(repoPath.resolve("parent").resolve("pom.xml"), Arrays.asList("<!-- changed -->"), StandardOpenOption.APPEND);

        final String output = executeBuild("-pl", "child4/subchild41", prop(Property.disableSelectedProjectsHandling, "true"),
                prop(Property.uncommitted, "true"), prop(Property.disableBranchComparison, "true"));

        assertThat(output)
                .doesNotContain("Building parent")
                .doesNotContain("Building child1")
                .doesNotContain("Building child4")
                .contains("Building subchild41")
                .contains("default-compile");
    }

    @Test
    public void testOnly_noDependent() throws Exception {
        checkout(Branch.DEVELOP);

        Path testResPath = Files.createDirectories(
                repoPath.resolve("parent").resolve("child6").resolve("src").resolve("test").resolve("resources"));
        Files.write(testResPath.resolve("changed.xml"), new byte[0]);

        final String output = executeBuild(prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .doesNotContain("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .contains("Building child6")
                .doesNotContain("Building testJarDependency")
                .doesNotContain("Building testJarDependent");
    }

    @Test
    public void testOnly_withDependent() throws Exception {
        checkout(Branch.DEVELOP);

        Path testResPath = Files.createDirectories(
                repoPath.resolve("parent").resolve("testJarDependency").resolve("src").resolve("test").resolve("resources"));
        Files.write(testResPath.resolve("changed.xml"), new byte[0]);

        final String output = executeBuild(prop(Property.disableBranchComparison, "true"));

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .doesNotContain("Building subchild2")
                .doesNotContain("Building child3")
                .doesNotContain("Building child4")
                .doesNotContain("Building subchild41")
                .doesNotContain("Building child6")
                .contains("Building testJarDependency")
                .contains("Building testJarDependent");
    }

    @Test
    public void quarkusScenario_noChanges() throws Exception {
        final String output = executeBuild(quarkusScenarioProps());

        assertThat(output).contains("Executing validate goal on current project only")
                .contains("Building parent")
                .doesNotContain("Building bom")
                .doesNotContain("Building child1")
                .doesNotContain("Building child2");
    }

    @Test
    public void quarkusScenario_bomChanged() throws Exception {
        Files.write(repoPath.resolve("quarkus-scenario").resolve("bom").resolve("pom.xml"), Arrays.asList("<!-- changed -->"), StandardOpenOption.APPEND);

        final String output = executeBuild(quarkusScenarioProps());

        assertThat(output)
                .doesNotContain("Building parent")
                .contains("Building bom")
                .doesNotContain("Building child1")
                .contains("Building child2")
                .contains("Building child3");
    }

    @Test
    public void quarkusScenario_bomChanged_notSelected() throws Exception {

        Files.write(repoPath.resolve("quarkus-scenario").resolve("bom").resolve("pom.xml"), Arrays.asList("<!-- changed -->"), StandardOpenOption.APPEND);

        final String output = executeBuild(quarkusScenarioProps("-pl", "child3", prop(Property.disableSelectedProjectsHandling, "true")));

        assertThat(output)
                .doesNotContain("Building parent")
                .doesNotContain("Building bom")
                .doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .contains("Building child3")
                .contains("No sources to compile"); // verifies that not only validate is called
    }

    private void checkout(Branch branch) throws GitAPIException, CheckoutConflictException, RefAlreadyExistsException,
            RefNotFoundException, InvalidRefNameException {
        Git git = localRepoMock.getGit();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
        git.checkout().setName(branch.branchName).call();
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
        String output = ProcessUtils.startAndWaitForProcess(command, localRepoMock.getRepoDir(),
                line -> !LOG_LINE_FILTER_PATTERN.matcher(line).matches());
        if (logOutput) {
            LOGGER.info("Output of {}({}):\n{}", testDisplayName, String.join(" ", command), output);
        }
        latestBuildOutput = output;
        return output;
    }

    protected static String prop(Property property, String value) {
        String propString =  "-D" + property.prefixedName();
        if (value != null && !value.isEmpty()) {
            propString += "=" + value;
        }
        return propString;
    }

    protected static String[] quarkusScenarioProps(String... additionalArgs) {
        List<String> argsList = new ArrayList<>();
        argsList.add(QUARKUS_POMFILE_ARG);
        argsList.add(prop(Property.baseBranch, Branch.QUARKUS.branchName));
        argsList.add(prop(Property.disableBranchComparison, "true"));
        argsList.add(prop(Property.uncommitted, "true"));
        for (String additionalArg : additionalArgs) {
            argsList.add(additionalArg);
        }
        return argsList.toArray(new String[0]);
    }

    private enum Branch {

        DEVELOP("develop"),
        QUARKUS("quarkus");

        private final String branchName;

        Branch(String branchName) {
            this.branchName = branchName;
        }
    }
}

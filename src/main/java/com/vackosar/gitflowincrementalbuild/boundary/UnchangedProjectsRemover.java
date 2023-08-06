package com.vackosar.gitflowincrementalbuild.boundary;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.TypeAwareExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration.BuildUpstreamMode;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;

@Singleton
@Named
class UnchangedProjectsRemover {

    private static final String MAVEN_TEST_SKIP = "maven.test.skip";
    private static final String MAVEN_TEST_SKIP_EXEC = "skipTests";
    private static final String TEST_JAR_DETECTED = "Dependency with test-jar goal detected. Will compile test sources.";
    private static final String GOAL_TEST_JAR = "test-jar";
    private static final String PCKG_POM = "pom";

    private Logger logger = LoggerFactory.getLogger(UnchangedProjectsRemover.class);

    @Inject private ChangedProjects changedProjects;

    @Inject private GitProvider gitProvider;

    private final Map<String, Set<MavenProject>> downstreamCache = new HashMap<>();

    void act(Configuration config) {
        try {
            doAct(config);
        } finally {
            // don't be a memory hog
            downstreamCache.clear();
        }
    }

    private void doAct(Configuration config) {
        LazyMavenProjectComparator projectComparator = new LazyMavenProjectComparator(config.mavenSession);
        // remove a possibly existing logfile of a previous run (so that e.g. SkipExecutionException doesn't leave behind an empty file like in < 4.5.0)
        config.logImpactedTo.ifPresent(logFilePath -> {
            try {
                Files.deleteIfExists(logFilePath);
            } catch (IOException e) {
                logger.warn("Could not delete '" + logFilePath + "', file might contain outdated projects!", e);
            }
        });

        final Set<MavenProject> selected;
        if (config.disableSelectedProjectsHandling) {
            selected = Collections.emptySet();
        } else {
            selected = ProjectSelectionUtil.gatherSelectedProjects(config.mavenSession);

            // before checking for any changes, check whether there are _only_ explicitly selected projects (-pl) which have the highest priority
            if (onlySelectedModulesPresent(selected, config.mavenSession)) {
                printDelimiter();
                logger.info("Building explicitly selected projects (without any adjustment): {}",
                        config.mavenSession.getProjects().stream().map(MavenProject::getArtifactId).collect(Collectors.joining(", ")));
                config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(selected, logFilePath, projectComparator, config));
                return;
            }

            // do nothing if:
            // - building non-recursively (-N)
            // - or only one "leaf" project/module is present (cases: mvn -f ... or cd ... or unusual case of non-multi-module project)
            // the assumption here (similar to the -pl approach above): the user has decided to build a single module, so don't mess with that
            if (!config.mavenSession.getRequest().isRecursive() || onlySingleLeafModulePresent(config)) {
                printDelimiter();
                logger.info("Building single project (without any adjustment): {}", config.currentProject.getArtifactId());
                config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(
                        Collections.singleton(config.currentProject), logFilePath, projectComparator, config));
                return;
            }
        }

        final Set<MavenProject> changed = changedProjects.get(config);
        printDelimiter();
        if (changed.isEmpty()) {
            handleNoChangesDetected(selected, projectComparator, config);
            config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(Collections.emptySet(), logFilePath, projectComparator, config));
            return;
        }
        logProjects(changed, "Changed Artifacts:", projectComparator, config.mavenSession);

        final Set<MavenProject> impacted = calculateImpactedProjects(selected, changed, config);

        config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(impacted, logFilePath, projectComparator, config));

        if (!config.buildAll) {
            modifyProjectList(selected, changed, impacted, projectComparator, config);
        } else {
            config.mavenSession.getProjects().stream()
                    .filter(proj -> !impacted.contains(proj))
                    .forEach(proj -> applyUpstreamModuleArgs(proj, config));
        }
    }

    private boolean onlySelectedModulesPresent(Set<MavenProject> selected, MavenSession mavenSession) {
        return !selected.isEmpty() && mavenSession.getProjects().equals(new ArrayList<>(selected));
    }

    private boolean onlySingleLeafModulePresent(Configuration config) {
        // note: explicit check for modules to cover -N case
        return config.mavenSession.getProjects().size() == 1 && config.currentProject.getModel().getModules().isEmpty();
    }

    private void handleNoChangesDetected(Set<MavenProject> selected, LazyMavenProjectComparator projectComparator, Configuration config) {
        if (!selected.isEmpty()) {
            logger.info("No changed artifacts detected: Building just explicitly selected projects (and their upstream and/or downstream, if requested).");
            // note: "only selected" case was handled before, so we have up- and/or downstream projects in the session as well
            Set<MavenProject> selectedAndDownstream = selected.stream()
                    .flatMap(proj -> streamProjectWithDownstreamProjects(proj, config))
                    .collect(Collectors.toSet());
            // handle upstream
            if (Configuration.isMakeBehaviourActive(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM, config.mavenSession)) {
                if (config.buildUpstreamMode == BuildUpstreamMode.NONE) {
                    // only retain selected and downstream
                    config.mavenSession.setProjects(selectedAndDownstream.stream()
                            .sorted(projectComparator)
                            .collect(Collectors.toList()));
                } else {
                    // applyUpstreamModuleArgs
                    config.mavenSession.getProjects().stream()
                            .filter(proj -> !selectedAndDownstream.contains(proj))
                            .forEach(proj -> applyUpstreamModuleArgs(proj, config));
                }
            }
            // handle downstream (remove downstream projects if configured)
            if (Configuration.isMakeBehaviourActive(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM, config.mavenSession)
                    && !config.buildDownstream) {
                config.mavenSession.setProjects(config.mavenSession.getProjects().stream()  // retain order, without projectComparator
                        .filter(proj -> selected.contains(proj) || !selectedAndDownstream.contains(proj))
                        .collect(Collectors.toList()));
            }
        } else if (config.buildAllIfNoChanges) {
            logger.info("No changed artifacts detected: Building all modules in buildAll mode.");
            logger.info("- skip tests: {}", config.skipTestsForUpstreamModules);
            logger.info("- additional args: {}", config.argsForUpstreamModules);
            config.mavenSession.getProjects().stream().forEach(proj -> applyUpstreamModuleArgs(proj, config));
        } else {
            logger.info("No changed artifacts detected: Executing validate goal on current project only, skipping all submodules.");
            config.mavenSession.setProjects(Collections.singletonList(
                    applyUpstreamModuleArgs(config.currentProject, config)));
            config.mavenSession.getGoals().clear();
            config.mavenSession.getGoals().add("validate");
        }
    }

    private Set<MavenProject> calculateImpactedProjects(Set<MavenProject> selected, Set<MavenProject> changed, Configuration config) {
        Stream<MavenProject> impacted = selected.isEmpty() ? changed.stream() : selected.stream();
        // note: buildAll *always* needs impacted incl. downstream, otherwise applyNotImpactedModuleArgs() might disable tests etc. for downstream modules!
        if (config.buildAll || config.buildDownstream) {
            impacted = impacted.flatMap(proj -> streamProjectWithDownstreamProjects(proj, config));
        }
        return impacted
                .filter(config.mavenSession.getProjects()::contains)   // not deselected
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void writeImpactedLogFile(Set<MavenProject> impacted, Path logFilePath, LazyMavenProjectComparator projectComparator, Configuration config) {
        List<String> projectsToLog;
        if (impacted.isEmpty()) {
            projectsToLog = Collections.emptyList();
        } else {
            Path projectRootDir = gitProvider.getProjectRoot(config);
            projectsToLog = impacted.stream()
                    .sorted(projectComparator)
                    .map(proj -> projectRootDir.relativize(proj.getBasedir().toPath()).toString())
                    .collect(Collectors.toList());
        }
        logger.debug("Writing impacted projects to {}: {}", logFilePath, projectsToLog);
        try {
            Path parentDir = logFilePath.toAbsolutePath().getParent();
            // Check if the parent Directory to the logFilePath exists. If not create it.
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.write(logFilePath, projectsToLog, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write impacted projects to " + logFilePath + ": " + impacted, e);
        }
    }

    private void modifyProjectList(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted,
            LazyMavenProjectComparator projectComparator, Configuration config) {
        Set<MavenProject> rebuild = calculateRebuildProjects(selected, changed, impacted, config);
        if (rebuild.isEmpty()) {
            handleNoChangesDetected(selected, projectComparator, config);
        } else {
            if (!config.forceBuildModules.isEmpty() || !config.forceBuildModulesConditionally.isEmpty()) {

                List<Pattern> conditionalPatterns = config.forceBuildModulesConditionally.entrySet().stream()
                        .filter(entry -> impacted.stream()
                                .anyMatch(proj -> entry.getKey().matcher(proj.getArtifactId()).matches()))
                        .map(Entry::getValue)
                        .collect(Collectors.toList());

                Set<MavenProject> forceBuildModules = config.mavenSession.getProjects().stream()
                        .filter(proj -> !rebuild.contains(proj))
                        .filter(proj -> matchesAny(proj.getArtifactId(), config.forceBuildModules)
                                || matchesAny(proj.getArtifactId(), conditionalPatterns))
                        .map(proj -> applyUpstreamModuleArgs(proj, config))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                rebuild.addAll(forceBuildModules);
            }
            config.mavenSession.setProjects(rebuild.stream()
                    .sorted(projectComparator)
                    .collect(Collectors.toList()));
        }
    }

    private Set<MavenProject> calculateRebuildProjects(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted,
            Configuration config) {
        BuildUpstreamMode buildUpstreamMode = config.buildUpstreamMode;

        Set<MavenProject> upstreamRequiringProjects;
        switch (buildUpstreamMode) {
            case NONE:
                // just use impacted
                return impacted;
            case CHANGED:
                upstreamRequiringProjects = selected.isEmpty() ? changed : selected;
                break;
            case IMPACTED:
                upstreamRequiringProjects = impacted;
                break;
            default:
                throw new IllegalStateException("Unsupported BuildUpstreamMode: " + buildUpstreamMode);
        }
        Set<MavenProject> upstreamProjects = upstreamRequiringProjects.stream()
                .flatMap(proj -> streamUpstreamProjects(proj, config.mavenSession))
                .filter(proj -> !impacted.contains(proj))
                .peek(proj -> applyUpstreamModuleArgs(proj, config))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return config.mavenSession.getProjects().stream()
                .filter(proj -> impacted.contains(proj) || upstreamProjects.contains(proj))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private MavenProject applyUpstreamModuleArgs(MavenProject mavenProject, Configuration config) {
        final Properties projectProperties = mavenProject.getProperties();
        if (config.skipTestsForUpstreamModules) {
            if (projectDeclaresTestJarGoal(mavenProject)) {
                logger.debug("{}: {}", mavenProject.getArtifactId(), TEST_JAR_DETECTED);
                projectProperties.setProperty(MAVEN_TEST_SKIP_EXEC, Boolean.TRUE.toString());
            } else {
                projectProperties.setProperty(MAVEN_TEST_SKIP, Boolean.TRUE.toString());
            }
        }
        config.argsForUpstreamModules.forEach(projectProperties::setProperty);
        return mavenProject;
    }

    private boolean projectDeclaresTestJarGoal(MavenProject project) {
        return project.getBuildPlugins().stream()
                .flatMap(proj -> proj.getExecutions().stream())
                .flatMap(e -> e.getGoals().stream())
                .anyMatch(GOAL_TEST_JAR::equals);
    }

    private void logProjects(Set<MavenProject> projects, String title, LazyMavenProjectComparator projectComparator, MavenSession mavenSession) {
        logger.info(title);
        logger.info("");
        projects.stream()
                .sorted(projectComparator)
                .map(proj -> {
                    String entry = proj.getArtifactId();
                    if (!mavenSession.getProjects().contains(proj)) {
                        entry += " (but deselected)";
                    }
                    return entry;
                })
                .forEach(logger::info);
        logger.info("");
    }

    private void printDelimiter() {
        logger.info("------------------------------------------------------------------------");
    }

    private Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project, Configuration config) {
        return streamProjectWithDownstreamProjects(project, false, config);
    }

    private Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project, boolean testJarOnly, Configuration config) {
        final String cacheKey = project.hashCode() + "_" + testJarOnly;
        // note: not using computeIfAbsent() here since it would break recursive invocations with ConcurrentModificationException
        Set<MavenProject> downstream = downstreamCache.get(cacheKey);
        if (downstream == null) {
            downstream = new LinkedHashSet<>();
            downstream.add(project);
            if (testJarOnly || Boolean.TRUE.equals(project.getContextValue(ChangedProjects.CTX_TEST_ONLY))) {
                if (projectDeclaresTestJarGoal(project)) {
                    config.mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, false).stream()
                            .flatMap(downstreamProj -> getTestJarDependencyScope(downstreamProj, project)
                                    .map(depScope -> streamProjectWithDownstreamProjects(downstreamProj, depScope.equals("test"), config))  // recursion!
                                    .orElseGet(Stream::empty))
                            .filter(downstreamProj -> isDownstreamModuleNotExcluded(downstreamProj, config))
                            .forEach(downstream::add);
                }
            } else {
                config.mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true).stream()
                        .filter(downstreamProj -> isDownstreamModuleNotExcluded(downstreamProj, config))
                        .forEach(downstream::add);
            }
            if (PCKG_POM.equals(project.getPackaging())) {    // performance hint: bomArtifactIdRegex or similar could speed things up
                downstream.addAll(findBOMDownstreamProjects(project, downstream, config));
            }
            downstreamCache.put(cacheKey, downstream);
        }
        return downstream.stream();
    }

    private Optional<String> getTestJarDependencyScope(MavenProject project, MavenProject testJarProvidingProject) {
        return project.getDependencies().stream()
                .filter(dep -> dep.getType().equals(GOAL_TEST_JAR)
                        && dep.getArtifactId().equals(testJarProvidingProject.getArtifactId())
                        && dep.getGroupId().equals(testJarProvidingProject.getGroupId())
                        && dep.getVersion().equals(testJarProvidingProject.getVersion()))
                .findFirst()
                .map(dep -> Optional.of(dep.getScope()).orElse("compile"));
    }

    private boolean isDownstreamModuleNotExcluded(MavenProject proj, Configuration config) {
        return !config.excludeDownstreamModulesPackagedAs.contains(proj.getPackaging());
    }

    private Set<MavenProject> findBOMDownstreamProjects(MavenProject potentialBOMProject, Set<MavenProject> downstream, Configuration config) {
        return config.mavenSession.getAllProjects().stream()    // "All" is crucial to properly handle de-selected BOM case (with dsph)
                .filter(proj -> !downstream.contains(proj)) // optimization
                .filter(proj -> isDownstreamModuleNotExcluded(proj, config))
                .filter(proj -> importsBOM(proj, potentialBOMProject, config))
                .flatMap(proj -> streamProjectWithDownstreamProjects(proj, config)) // (indirect) recursion!
                .filter(config.mavenSession.getProjects()::contains)   // skip projects not part of the actual reactor (see getAllProjects() further up)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Boolean importsBOM(MavenProject project, MavenProject potentialBOMProject, Configuration config) {
        return Optional.ofNullable(project.getOriginalModel())    // >original< model is crucial since BOM deps are gone in effective model
                .map(Model::getDependencyManagement)
                .map(depMgtm -> depMgtm.getDependencies().stream().anyMatch(dep -> isBOMImport(dep, potentialBOMProject, project, config)))
                .orElse(false);
    }

    private boolean isBOMImport(Dependency dependency, MavenProject potentialBOMProject, MavenProject depDefiningProject, Configuration config) {
        LazyExpressionEvaluator evaluator = new LazyExpressionEvaluator(config.mavenSession, depDefiningProject);
        return Objects.equals(evaluator.evaluate(dependency.getType()), PCKG_POM)
                && Objects.equals(evaluator.evaluate(dependency.getScope()), Artifact.SCOPE_IMPORT)
                && Objects.equals(evaluator.evaluate(dependency.getArtifactId()), potentialBOMProject.getArtifactId())
                && Objects.equals(evaluator.evaluate(dependency.getGroupId()), potentialBOMProject.getGroupId())
                && Objects.equals(evaluator.evaluate(dependency.getVersion()), potentialBOMProject.getVersion());
    }

    private Stream<MavenProject> streamUpstreamProjects(MavenProject project, MavenSession mavenSession) {
        return mavenSession.getProjectDependencyGraph().getUpstreamProjects(project, true).stream();
    }

    private boolean matchesAny(final String str, Collection<Pattern> patterns) {
        return !patterns.isEmpty() && patterns.stream().anyMatch(pattern -> pattern.matcher(str).matches());
    }

    private static class ProjectSelectionUtil {

        static Set<MavenProject> gatherSelectedProjects(MavenSession mavenSession) {
            List<String> selectors = mavenSession.getRequest().getSelectedProjects();
            if (selectors.isEmpty()) {
                return Collections.emptySet();
            }
            File reactorDirectory = Optional.ofNullable(mavenSession.getRequest().getBaseDirectory()).map(File::new).orElse(null);
            return mavenSession.getProjects().stream()
                    .filter(proj -> selectors.stream().anyMatch(sel -> matchesSelector(proj, sel, reactorDirectory)))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // inspired by: org.apache.maven.graph.DefaultGraphBuilder.isMatchingProject(MavenProject, String, File)
        private static boolean matchesSelector(MavenProject project, String selector, File reactorDirectory) {
            if (selector.contains(":")) {   // [groupId]:artifactId
                String id = ':' + project.getArtifactId();
                if (id.equals(selector)) {
                    return true;
                }

                id = project.getGroupId() + id;
                if (id.equals(selector)) {
                    return true;
                }
            } else if (reactorDirectory != null) { // relative path, e.g. "sub", "../sub" or "."
                File selectedProject = new File(new File(reactorDirectory, selector).toURI().normalize());

                if (selectedProject.isFile()) {
                    return selectedProject.equals(project.getFile());
                } else if (selectedProject.isDirectory()) {
                    return selectedProject.equals(project.getBasedir());
                }
            }
            return false;
        }
    }

    /**
     * Compares projects by their position/index in the reactor ({@link MavenSession#getProjects()}) or secondarily in {@link MavenSession#getAllProjects()}.
     */
    static class LazyMavenProjectComparator implements Comparator<MavenProject> {

        private final MavenSession mavenSession;

        private Map<MavenProject, Integer> indexMap;

        public LazyMavenProjectComparator(MavenSession mavenSession) {
            this.mavenSession = mavenSession;
        }

        @Override
        public int compare(MavenProject proj1, MavenProject proj2) {
            if (indexMap == null) {
                List<MavenProject> projects = mavenSession.getProjects();
                indexMap = IntStream.range(0, projects.size()).boxed().collect(Collectors.toMap(projects::get, i -> i));
                // projects might be a subset of all projects (e.g. when -pl is used)
                List<MavenProject> allProjects = mavenSession.getAllProjects();
                if (allProjects.size() > projects.size()) {
                    for (int i = 0; i < allProjects.size(); i++) {
                        MavenProject proj = allProjects.get(i);
                        if (!indexMap.containsKey(proj)) {
                            indexMap.put(proj, i + 100_000);  // non-reactor project receives a penalty offset (sorted to the back)
                        }
                    }
                }
            }
            return indexMap.getOrDefault(proj1, Integer.MAX_VALUE).compareTo(indexMap.getOrDefault(proj2, Integer.MAX_VALUE));
        }
    }

    private static class LazyExpressionEvaluator {

        private static final Logger LOGGER = LoggerFactory.getLogger(LazyExpressionEvaluator.class);

        private final MavenSession session;
        private final MavenProject project;

        private TypeAwareExpressionEvaluator evaluator;

        public LazyExpressionEvaluator(MavenSession session, MavenProject project) {
            this.session = session;
            this.project = project;
        }

        public String evaluate(String expression) {
            if (expression == null || !expression.contains("${")) {
                return expression;
            }
            if (evaluator == null) {
                // set project on cloned session otherwise properties might be resolved from a more or less unrelated project
                MavenSession clonedSession = session.clone();
                clonedSession.setCurrentProject(project);
                // there is also a ctor without MojoExecution parameter but evaluate() will then fail with a NPE (sic!)
                // see also: https://issues.apache.org/jira/browse/MNG-6982
                evaluator = new PluginParameterExpressionEvaluator(clonedSession, new MojoExecution(new MojoDescriptor()));
            }
            try {
                return evaluator.evaluate(expression, String.class).toString();
            } catch (ExpressionEvaluationException e) {
                LOGGER.warn("Failed to evaluate: " + expression, e);
                return expression;
            }
        }
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration.BuildUpstreamMode;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Named
class UnchangedProjectsRemover {

    private static final String MAVEN_TEST_SKIP = "maven.test.skip";
    private static final String MAVEN_TEST_SKIP_EXEC = "skipTests";
    private static final String TEST_JAR_DETECTED = "Dependency with test-jar goal detected. Will compile test sources.";
    private static final String GOAL_TEST_JAR = "test-jar";

    private Logger logger = LoggerFactory.getLogger(UnchangedProjectsRemover.class);

    @Inject private ChangedProjects changedProjects;

    void act(Configuration config) throws GitAPIException, IOException {
        // ensure to write logfile for impaced (even if just empty)
        config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(Collections.emptySet(), logFilePath, config.mavenSession));

        // before checking for any changes, check whether there are _only_ explicitly selected projects (-pl) which have the highest priority
        final Set<MavenProject> selected = ProjectSelectionUtil.gatherSelectedProjects(config.mavenSession);
        if (onlySelectedModulesPresent(selected, config.mavenSession)) {
            printDelimiter();
            logger.info("Building explicitly selected projects (without any adjustment): {}",
                    config.mavenSession.getProjects().stream().map(MavenProject::getArtifactId).collect(Collectors.joining(", ")));
            return;
        }
        // do nothing if:
        // - building non-recursively (-N)
        // - or only one "leaf" project/module is present (cases: mvn -f ... or cd ... or unusual case of non-multi-module project)
        // the assumption here (similar to the -pl approach above): the user has decided to build a single module, so don't mess with that
        if (!config.mavenSession.getRequest().isRecursive() || onlySingleLeafModulePresent(config)) {
            printDelimiter();
            logger.info("Building single project (without any adjustment): {}", config.currentProject.getArtifactId());
            return;
        }

        final Set<MavenProject> changed = changedProjects.get(config);
        printDelimiter();
        if (changed.isEmpty()) {
            handleNoChangesDetected(selected, config);
            return;
        }
        logProjects(changed, "Changed Artifacts:", config.mavenSession);

        final Set<MavenProject> impacted = calculateImpactedProjects(selected, changed, config);

        config.logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(impacted, logFilePath, config.mavenSession));

        if (!config.buildAll) {
            modifyProjectList(selected, changed, impacted, config);
        } else {
            config.mavenSession.getProjects().stream()
                    .filter(proj -> !impacted.contains(proj))
                    .forEach(proj -> applyUpstreamModuleArgs(proj, config));
        }
    }

    private void writeImpactedLogFile(Set<MavenProject> impacted, Path logFilePath, MavenSession mavenSession) {
        List<String> projectsToLog = impacted.isEmpty()
                ? Collections.emptyList()
                : mavenSession.getProjects().stream()   // write in proper order
                        .filter(impacted::contains)
                        .map(proj -> proj.getBasedir().getPath())
                        .collect(Collectors.toList());
        logger.debug("Writing impacted projects to {}: {}", logFilePath, projectsToLog);
        try {
            Files.write(logFilePath, projectsToLog, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write impacted projects to " + logFilePath + ": " + impacted, e);
        }
    }

    private boolean onlySelectedModulesPresent(Set<MavenProject> selected, MavenSession mavenSession) {
        return !selected.isEmpty() && mavenSession.getProjects().equals(new ArrayList<>(selected));
    }

    private boolean onlySingleLeafModulePresent(Configuration config) {
        // note: explicit check for modules to cover -N case
        return config.mavenSession.getProjects().size() == 1 && config.currentProject.getModel().getModules().isEmpty();
    }

    private void handleNoChangesDetected(Set<MavenProject> selected, Configuration config) {
        if (!selected.isEmpty()) {
            // note: need to check make behaviour additionally because downstream projects will only be present for -pl when also -amd is set
            if (config.buildDownstream && Configuration.isMakeBehaviourActive(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM, config.mavenSession)) {
                logger.info("No changed artifacts detected: Building explicitly selected projects and their dependents.");
                config.mavenSession.setProjects(selected.stream()
                        .flatMap(proj -> streamProjectWithDownstreamProjects(proj, config))
                        .distinct()
                        .collect(Collectors.toList()));
            } else {
                logger.info("No changed artifacts detected: Building just explicitly selected projects.");
                config.mavenSession.setProjects(new ArrayList<>(selected));
            }
        } else if (config.buildAllIfNoChanges) {
            logger.info("No changed artifacts detected: Building all modules in buildAll mode.");
            logger.info("- skip tests: {}", config.skipTestsForUpstreamModules);
            logger.info("- additional args: {}", config.argsForUpstreamModules);
            config.mavenSession.getProjects().stream().forEach(proj -> applyUpstreamModuleArgs(proj, config));
        } else {
            logger.info("No changed artifacts detected: Executing validate goal on current project only, skipping all submodules.");
            config.mavenSession.setProjects(Collections.singletonList(config.currentProject));
            config.mavenSession.getGoals().clear();
            config.mavenSession.getGoals().add("validate");
        }
    }

    private Set<MavenProject> calculateImpactedProjects(Set<MavenProject> selected, Set<MavenProject> changed, Configuration config) {
        Stream<MavenProject> impacted = selected.isEmpty() ? changed.stream() : Stream.concat(selected.stream(), changed.stream()).distinct();
        // note: buildAll *always* needs impacted incl. downstream, otherwise applyNotImpactedModuleArgs() might disable tests etc. for downstream modules!
        if (config.buildAll || config.buildDownstream) {
            impacted = impacted.flatMap(proj -> streamProjectWithDownstreamProjects(proj, config));
        }
        return impacted
                .filter(config.mavenSession.getProjects()::contains)   // not deselected
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void modifyProjectList(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted, Configuration config) {
        Set<MavenProject> rebuild = calculateRebuildProjects(selected, changed, impacted, config);
        if (rebuild.isEmpty()) {
            handleNoChangesDetected(selected, config);
        } else if (!config.forceBuildModules.isEmpty()) {
            Set<MavenProject> forceBuildModules = config.mavenSession.getProjects().stream()
                    .filter(proj -> !rebuild.contains(proj))
                    .filter(proj -> matchesAny(proj.getArtifactId(), config.forceBuildModules))
                    .map(proj -> applyUpstreamModuleArgs(proj, config))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            config.mavenSession.setProjects(config.mavenSession.getProjects().stream()
                    .filter(proj -> forceBuildModules.contains(proj) || rebuild.contains(proj))
                    .collect(Collectors.toList()));
        } else {
            config.mavenSession.setProjects(new ArrayList<>(rebuild));
        }
    }

    private Set<MavenProject> calculateRebuildProjects(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted,
            Configuration config) {
        BuildUpstreamMode buildUpstreamMode = config.buildUpstreamMode;
        Set<MavenProject> upstreamProjects;

        if (!selected.isEmpty()) {
            // note: buildDownstream=false is not relevant here since -amd might have been specified
            //       and we need all downstreams to subtract them from the project list to find the upstreams
            Set<MavenProject> selectedWithDownstream = selected.stream()
                    .flatMap(proj -> streamProjectWithDownstreamProjects(proj, config))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            switch (buildUpstreamMode) {
                case NONE:
                    // just use impacted that are selected and the downstreams of the selected
                    return impacted.stream()
                            .filter(selectedWithDownstream::contains)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                case CHANGED:
                    // fall-through
                case IMPACTED:
                    upstreamProjects = changed.stream()
                            .filter(proj -> !selectedWithDownstream.contains(proj))
                            .flatMap(proj -> streamProjectWithDownstreamProjects(proj, config))
                            .filter(proj -> !selectedWithDownstream.contains(proj))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    break;
                default:
                    throw new IllegalStateException("Unsupported BuildUpstreamMode: " + buildUpstreamMode);
            }
        } else {
            Set<MavenProject> upstreamRequiringProjects;
            switch (buildUpstreamMode) {
                case NONE:
                    // just use impacted
                    return impacted;
                case CHANGED:
                    upstreamRequiringProjects = changed;
                    break;
                case IMPACTED:
                    upstreamRequiringProjects = impacted;
                    break;
                default:
                    throw new IllegalStateException("Unsupported BuildUpstreamMode: " + buildUpstreamMode);
            }
            upstreamProjects = upstreamRequiringProjects.stream()
                    .flatMap(proj -> streamUpstreamProjects(proj, config.mavenSession))
                    .filter(proj -> !impacted.contains(proj))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        upstreamProjects.forEach(proj -> applyUpstreamModuleArgs(proj, config));

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

    private void logProjects(Set<MavenProject> projects, String title, MavenSession mavenSession) {
        logger.info(title);
        logger.info("");
        projects.stream()
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
        return Stream.concat(
            Stream.of(project),
            config.mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true).stream()
                    .filter(proj -> !config.excludeDownstreamModulesPackagedAs.contains(proj.getPackaging())));
    }

    private Stream<MavenProject> streamUpstreamProjects(MavenProject project, MavenSession mavenSession) {
        return mavenSession.getProjectDependencyGraph().getUpstreamProjects(project, true).stream();
    }

    private boolean matchesAny(final String str, Collection<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(str).matches());
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
}

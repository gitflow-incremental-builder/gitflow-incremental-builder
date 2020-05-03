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
    @Inject private MavenSession mavenSession;
    @Inject private Configuration.Provider configProvider;

    void act() throws GitAPIException, IOException {
        // ensure to write logfile for impaced (even if just empty)
        configProvider.get().logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(Collections.emptySet(), logFilePath));

        // before checking for any changes, check whether there are _only_ explicitly selected projects (-pl) which have the highest priority
        final Set<MavenProject> selected = ProjectSelectionUtil.gatherSelectedProjects(mavenSession);
        if (onlySelectedModulesPresent(selected)) {
            printDelimiter();
            logger.info("Building explicitly selected projects (without any adjustment): {}",
                    mavenSession.getProjects().stream().map(MavenProject::getArtifactId).collect(Collectors.joining(", ")));
            return;
        }
        // do nothing if only one "leaf" project/module is present (cases: mvn -f ... or cd ... or unusual case of non-multi-module project)
        // the assumption here (similar to the -pl approach above): the user has decided to build a single module, so don't mess with that
        if (onlySingleLeafModulePresent()) {
            printDelimiter();
            logger.info("Building single project (without any adjustment): {}", mavenSession.getCurrentProject().getArtifactId());
            return;
        }

        final Set<MavenProject> changed = changedProjects.get();
        printDelimiter();
        if (changed.isEmpty()) {
            handleNoChangesDetected(selected);
            return;
        }
        logProjects(changed, "Changed Artifacts:");

        final Set<MavenProject> impacted = calculateImpactedProjects(selected, changed);

        configProvider.get().logImpactedTo.ifPresent(logFilePath -> writeImpactedLogFile(impacted, logFilePath));

        if (!configProvider.get().buildAll) {
            modifyProjectList(selected, changed, impacted);
        } else {
            mavenSession.getProjects().stream()
                    .filter(proj -> !impacted.contains(proj))
                    .forEach(this::applyUpstreamModuleArgs);
        }
    }

    private void writeImpactedLogFile(Set<MavenProject> impacted, Path logFilePath) {
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

    private boolean onlySelectedModulesPresent(Set<MavenProject> selected) {
        return !selected.isEmpty() && mavenSession.getProjects().equals(new ArrayList<>(selected));
    }

    private boolean onlySingleLeafModulePresent() {
        // note: explicit check for modules to cover -N case
        return mavenSession.getProjects().size() == 1 && mavenSession.getCurrentProject().getModel().getModules().isEmpty();
    }

    private void handleNoChangesDetected(Set<MavenProject> selected) {
        Configuration cfg = configProvider.get();
        if (!selected.isEmpty()) {
            // note: need to check make behaviour additionally because downstream projects will only be present for -pl when also -amd is set
            if (cfg.buildDownstream && Configuration.isMakeBehaviourActive(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM, mavenSession)) {
                logger.info("No changed artifacts detected: Building explicitly selected projects and their dependents.");
                mavenSession.setProjects(selected.stream()
                        .flatMap(this::streamProjectWithDownstreamProjects)
                        .distinct()
                        .collect(Collectors.toList()));
            } else {
                logger.info("No changed artifacts detected: Building just explicitly selected projects.");
                mavenSession.setProjects(new ArrayList<>(selected));
            }
        } else if (cfg.buildAllIfNoChanges) {
            logger.info("No changed artifacts detected: Building all modules in buildAll mode.");
            logger.info("- skip tests: {}", cfg.skipTestsForUpstreamModules);
            logger.info("- additional args: {}", cfg.argsForUpstreamModules);
            mavenSession.getProjects().stream().forEach(this::applyUpstreamModuleArgs);
        } else {
            logger.info("No changed artifacts detected: Executing validate goal on current project only, skipping all submodules.");
            mavenSession.setProjects(Collections.singletonList(mavenSession.getCurrentProject()));
            mavenSession.getGoals().clear();
            mavenSession.getGoals().add("validate");
        }
    }

    private Set<MavenProject> calculateImpactedProjects(Set<MavenProject> selected, Set<MavenProject> changed) {
        Configuration cfg = configProvider.get();
        Stream<MavenProject> impacted = selected.isEmpty() ? changed.stream() : Stream.concat(selected.stream(), changed.stream()).distinct();
        // note: buildAll *always* needs impacted incl. downstream, otherwise applyNotImpactedModuleArgs() might disable tests etc. for downstream modules!
        if (cfg.buildAll || cfg.buildDownstream) {
            impacted = impacted.flatMap(this::streamProjectWithDownstreamProjects);
        }
        return impacted
                .filter(mavenSession.getProjects()::contains)   // not deselected
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void modifyProjectList(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted) {
        Set<MavenProject> rebuild = calculateRebuildProjects(selected, changed, impacted);
        if (rebuild.isEmpty()) {
            handleNoChangesDetected(selected);
        } else if (!configProvider.get().forceBuildModules.isEmpty()) {
            Set<MavenProject> forceBuildModules = mavenSession.getProjects().stream()
                    .filter(proj -> !rebuild.contains(proj))
                    .filter(proj -> matchesAny(proj.getArtifactId(), configProvider.get().forceBuildModules))
                    .map(this::applyUpstreamModuleArgs)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            mavenSession.setProjects(mavenSession.getProjects().stream()
                    .filter(proj -> forceBuildModules.contains(proj) || rebuild.contains(proj))
                    .collect(Collectors.toList()));
        } else {
            mavenSession.setProjects(new ArrayList<>(rebuild));
        }
    }

    private Set<MavenProject> calculateRebuildProjects(Set<MavenProject> selected, Set<MavenProject> changed, Set<MavenProject> impacted) {
        BuildUpstreamMode buildUpstreamMode = configProvider.get().buildUpstreamMode;
        Set<MavenProject> upstreamProjects;

        if (!selected.isEmpty()) {
            // note: buildDownstream=false is not relevant here since -amd might have been specified
            //       and we need all downstreams to subtract them from the project list to find the upstreams
            Set<MavenProject> selectedWithDownstream = selected.stream()
                    .flatMap(this::streamProjectWithDownstreamProjects)
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
                            .flatMap(this::streamProjectWithDownstreamProjects)
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
                    .flatMap(this::streamUpstreamProjects)
                    .filter(proj -> !impacted.contains(proj))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        upstreamProjects.forEach(this::applyUpstreamModuleArgs);

        return mavenSession.getProjects().stream()
                .filter(proj -> impacted.contains(proj) || upstreamProjects.contains(proj))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private MavenProject applyUpstreamModuleArgs(MavenProject mavenProject) {
        final Properties projectProperties = mavenProject.getProperties();
        if (configProvider.get().skipTestsForUpstreamModules) {
            if (projectDeclaresTestJarGoal(mavenProject)) {
                logger.debug("{}: {}", mavenProject.getArtifactId(), TEST_JAR_DETECTED);
                projectProperties.setProperty(MAVEN_TEST_SKIP_EXEC, Boolean.TRUE.toString());
            } else {
                projectProperties.setProperty(MAVEN_TEST_SKIP, Boolean.TRUE.toString());
            }
        }
        configProvider.get().argsForUpstreamModules.forEach(projectProperties::setProperty);
        return mavenProject;
    }

    private boolean projectDeclaresTestJarGoal(MavenProject project) {
        return project.getBuildPlugins().stream()
                .flatMap(proj -> proj.getExecutions().stream())
                .flatMap(e -> e.getGoals().stream())
                .anyMatch(GOAL_TEST_JAR::equals);
    }

    private void logProjects(Set<MavenProject> projects, String title) {
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

    private Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project) {
        return Stream.concat(
            Stream.of(project),
            mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true).stream()
                    .filter(proj -> !configProvider.get().excludeDownstreamModulesPackagedAs.contains(proj.getPackaging())));
    }

    private Stream<MavenProject> streamUpstreamProjects(MavenProject project) {
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

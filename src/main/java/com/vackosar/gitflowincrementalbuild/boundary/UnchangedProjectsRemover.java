package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Named("gib.unchangedProjectsRemover")
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
        Set<MavenProject> changed = changedProjects.get();
        printDelimiter();
        logProjects(changed, "Changed Artifacts:");
        Set<MavenProject> impacted = changed.stream()
            .flatMap(this::streamProjectWithDependents)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!configProvider.get().buildAll) {
            Set<MavenProject> rebuild = getRebuildProjects(impacted);
            if (rebuild.isEmpty()) {
                logger.info("No changed artifacts to build. Executing validate goal on current project only.");
                mavenSession.setProjects(Collections.singletonList(mavenSession.getCurrentProject()));
                mavenSession.getGoals().clear();
                mavenSession.getGoals().add("validate");
            } else if (!configProvider.get().forceBuildModules.isEmpty()) {
                Stream<MavenProject> forceBuildModules = mavenSession.getProjects().stream()
                        .filter(p -> matchesAny(p.getArtifactId(), configProvider.get().forceBuildModules))
                        .filter(p -> !rebuild.contains(p))
                        .map(this::applyNotImpactedModuleArgs);
                mavenSession.setProjects(
                        Stream.concat(forceBuildModules, rebuild.stream()).collect(Collectors.toList()));
            } else {
                mavenSession.setProjects(new ArrayList<>(rebuild));
            }
        } else {
            mavenSession.getProjects().stream()
                    .filter(p -> !impacted.contains(p))
                    .forEach(this::applyNotImpactedModuleArgs);
        }
    }

    private Set<MavenProject> getRebuildProjects(Set<MavenProject> changedProjects) {
        if (configProvider.get().makeUpstream) {
            return Stream.concat(changedProjects.stream(), collectDependencies(changedProjects))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            return changedProjects;
        }
    }

    private Stream<MavenProject> collectDependencies(Set<MavenProject> changedProjects) {
        return changedProjects.stream()
                .flatMap(this::streamProjectWithDependencies)
                .filter(p -> ! changedProjects.contains(p))
                .map(this::applyNotImpactedModuleArgs);
    }

    private MavenProject applyNotImpactedModuleArgs(MavenProject mavenProject) {
        final Properties projectProperties = mavenProject.getProperties();
        if (configProvider.get().skipTestsForNotImpactedModules) {
            if (projectDeclaresTestJarGoal(mavenProject)) {
                logger.debug("{}: {}", mavenProject.getArtifactId(), TEST_JAR_DETECTED);
                projectProperties.setProperty(MAVEN_TEST_SKIP_EXEC, Boolean.TRUE.toString());
            } else {
                projectProperties.setProperty(MAVEN_TEST_SKIP, Boolean.TRUE.toString());
            }
        }
        configProvider.get().argsForNotImpactedModules.forEach(projectProperties::setProperty);
        return mavenProject;
    }

    private boolean projectDeclaresTestJarGoal(MavenProject mavenProject) {
        return mavenProject.getBuildPlugins().stream()
                .flatMap(p -> p.getExecutions().stream())
                .flatMap(e -> e.getGoals().stream())
                .anyMatch(GOAL_TEST_JAR::equals);
    }

    private void logProjects(Set<MavenProject> projects, String title) {
        logger.info(title);
        logger.info("");
        projects.stream().map(MavenProject::getArtifactId).forEach(logger::info);
        logger.info("");
    }

    private void printDelimiter() {
        logger.info("------------------------------------------------------------------------");
    }

    private Stream<MavenProject> streamProjectWithDependents(MavenProject project) {
        return Stream.concat(
            Stream.of(project),
            mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true).stream()
                .filter(p -> !configProvider.get().excludeTransitiveModulesPackagedAs.contains(p.getPackaging())));
    }

    private Stream<MavenProject> streamProjectWithDependencies(MavenProject project) {
        return Stream.concat(
            Stream.of(project),
            mavenSession.getProjectDependencyGraph().getUpstreamProjects(project, true).stream());
    }

    private boolean matchesAny(final String str, Collection<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(str).matches());
    }
}


package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Singleton;
import com.vackosar.gitflowincrementalbuild.control.ChangedProjects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class UnchangedProjectsRemover {

    private static final String MAVEN_TEST_SKIP = "maven.test.skip";
    public static final String TEST_JAR_DETECTED = "Dependency with test-jar goal detected. Adding test-compile goal.";
    private static final String GOAL_TEST_COMPILE = "test-compile";
    private static final String GOAL_TEST_JAR = "test-jar";

    @Inject private Configuration configuration;
    @Inject private Logger logger;
    @Inject private ChangedProjects changedProjects;
    @Inject private MavenSession mavenSession;

    public void act() throws GitAPIException, IOException {
        Set<MavenProject> changed = changedProjects.get();
        printDelimiter();
        logProjects(changed, "Changed Artifacts:");
        Set<MavenProject> impacted = mavenSession.getProjects().stream()
                .filter(changed::contains)
                .flatMap(p -> getAllDependents(mavenSession.getProjects(), p).stream())
                .collect(Collectors.toSet());
        if (!configuration.buildAll) {
            Set<MavenProject> rebuild = getRebuildProjects(impacted);
            if (rebuild.isEmpty()) {
                logger.info("No changed artifacts to build. Executing validate goal only.");
                mavenSession.getGoals().clear();
                mavenSession.getGoals().add("validate");
            } else {
                mavenSession.setProjects(new ArrayList<>(rebuild));
            }
        } else {
            mavenSession.getProjects().stream()
                    .filter(p -> !impacted.contains(p))
                    .forEach(this::ifSkipDependenciesTest);
        }
    }

    private Set<MavenProject> getRebuildProjects(Set<MavenProject> changedProjects) {
        if (configuration.makeUpstream) {
            return Stream.concat(changedProjects.stream(), collectDependencies(changedProjects)).collect(Collectors.toSet());
        } else {
            return changedProjects;
        }
    }

    private Stream<MavenProject> collectDependencies(Set<MavenProject> changedProjects) {
        return changedProjects.stream()
                .flatMap(this::ifMakeUpstreamGetDependencies)
                .filter(p -> ! changedProjects.contains(p))
                .map(this::ifSkipDependenciesTest);
    }

    private MavenProject ifSkipDependenciesTest(MavenProject mavenProject) {
        if (configuration.skipTestsForNotImpactedModules) {
            mavenProject.getProperties().setProperty(MAVEN_TEST_SKIP, Boolean.TRUE.toString());
            if (! mavenSession.getGoals().contains(GOAL_TEST_COMPILE) && projectDeclaresTestJarGoal(mavenProject)) {
                logger.info(TEST_JAR_DETECTED);
                logger.info("");
                mavenSession.getGoals().add(GOAL_TEST_COMPILE);
            }
        }
        return mavenProject;
    }

    private boolean projectDeclaresTestJarGoal(MavenProject mavenProject) {
        return mavenProject.getBuildPlugins().stream()
                .flatMap(p -> p.getExecutions().stream())
                .flatMap(e -> e.getGoals().stream())
                .filter(GOAL_TEST_JAR::equals).findAny()
                .isPresent();
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

    private Set<MavenProject> getAllDependents(List<MavenProject> projects, MavenProject project) {
        Set<MavenProject> result = new HashSet<>();
        result.add(project);
        for (MavenProject possibleDependent: projects) {
            if (isDependentOf(possibleDependent, project)) {
                result.addAll(getAllDependents(projects, possibleDependent));
            }
            if (project.equals(possibleDependent.getParent())) {
                result.addAll(getAllDependents(projects, possibleDependent));
            }
        }
        return result;
    }

    private Stream<MavenProject> ifMakeUpstreamGetDependencies(MavenProject mavenProject) {
        return getAllDependencies(mavenSession.getProjects(), mavenProject).stream();
    }

    private Set<MavenProject> getAllDependencies(List<MavenProject> projects, MavenProject project) {
        Set<MavenProject> dependencies = project.getDependencies().stream()
                .map(d -> convert(projects, d)).filter(Optional::isPresent).map(Optional::get)
                .flatMap(p -> getAllDependencies(projects, p).stream())
                .collect(Collectors.toSet());
        dependencies.add(project);
        return dependencies;
    }

    private boolean equals(MavenProject project, Dependency dependency) {
        return dependency.getArtifactId().equals(project.getArtifactId())
                && dependency.getGroupId().equals(project.getGroupId())
                && dependency.getVersion().equals(project.getVersion());
    }

    private Optional<MavenProject> convert(List<MavenProject> projects, Dependency dependency) {
        return projects.stream().filter(p -> equals(p, dependency)).findFirst();
    }

    private boolean isDependentOf(MavenProject possibleDependent, MavenProject project) {
        return possibleDependent.getDependencies().stream().anyMatch(d -> equals(project, d));
    }
}

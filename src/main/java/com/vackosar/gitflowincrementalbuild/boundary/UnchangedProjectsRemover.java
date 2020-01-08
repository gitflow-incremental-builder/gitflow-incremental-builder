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
        Set<MavenProject> changed = changedProjects.get();
        printDelimiter();
        logProjects(changed, "Changed Artifacts:");

        // important: buildAll *always* need impacted incl. donwstream, otherwise applyNotImpactedModuleArgs() might disable tests etc. for downstream modules!
        Configuration cfg = configProvider.get();
        Set<MavenProject> impacted = cfg.buildAll || cfg.buildDownstream
                ? changed.stream()
                        .flatMap(this::streamProjectWithDownstreamProjects)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                : changed;

        if (!cfg.buildAll) {
            modifyProjectList(changed, impacted);
        } else {
            mavenSession.getProjects().stream()
                    .filter(p -> !impacted.contains(p))
                    .forEach(this::applyUpstreamModuleArgs);
        }
    }

    private void modifyProjectList(Set<MavenProject> changed, Set<MavenProject> impacted) {
        Set<MavenProject> rebuild = getRebuildProjects(changed, impacted);
        if (rebuild.isEmpty()) {
            logger.info("No changed artifacts to build. Executing validate goal on current project only.");
            mavenSession.setProjects(Collections.singletonList(mavenSession.getCurrentProject()));
            mavenSession.getGoals().clear();
            mavenSession.getGoals().add("validate");
        } else if (!configProvider.get().forceBuildModules.isEmpty()) {
            Set<MavenProject> forceBuildModules = mavenSession.getProjects().stream()
                    .filter(p -> matchesAny(p.getArtifactId(), configProvider.get().forceBuildModules))
                    .filter(p -> !rebuild.contains(p))
                    .map(this::applyUpstreamModuleArgs)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            mavenSession.setProjects(mavenSession.getProjects().stream()
                    .filter(prj -> forceBuildModules.contains(prj) || rebuild.contains(prj))
                    .collect(Collectors.toList()));
        } else {
            mavenSession.setProjects(new ArrayList<>(rebuild));
        }
    }

    private Set<MavenProject> getRebuildProjects(Set<MavenProject> changed, Set<MavenProject> impacted) {
        Set<MavenProject> upstreamRequiringProjects;
        switch (configProvider.get().buildUpstreamMode) {
            case NONE:
                return impacted;    // just use impacted without any further processing
            case CHANGED:
                upstreamRequiringProjects = changed;
                break;
            case IMPACTED:
                upstreamRequiringProjects = impacted;
                break;
            default:
                throw new IllegalStateException("Unsupported BuildUpstreamMode: " + configProvider.get().buildUpstreamMode);
        }
        Stream<MavenProject> upstreamProjects = upstreamRequiringProjects.stream()
                .flatMap(this::streamUpstreamProjects)
                .filter(p -> ! changed.contains(p))
                .map(this::applyUpstreamModuleArgs);
        return Stream.concat(impacted.stream(), upstreamProjects)
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

    private Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project) {
        return Stream.concat(
            Stream.of(project),
            mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true).stream()
                    .filter(p -> !configProvider.get().excludeTransitiveModulesPackagedAs.contains(p.getPackaging())));
    }

    private Stream<MavenProject> streamUpstreamProjects(MavenProject project) {
        return mavenSession.getProjectDependencyGraph().getUpstreamProjects(project, true).stream();
    }

    private boolean matchesAny(final String str, Collection<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(str).matches());
    }
}


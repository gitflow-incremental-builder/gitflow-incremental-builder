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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class UnchangedProjectsRemover {

    @Inject private Configuration configuration;
    @Inject private Logger logger;
    @Inject private ChangedProjects changedProjects;
    @Inject private MavenSession mavenSession;

    public void act() throws GitAPIException, IOException {
        if (!configuration.enabled) {
            logger.info("GIB is disabled.");
        } else {
            Set<MavenProject> changed = changedProjects.get();
            printDelimiter();
            logProjects(changed, "Changed Artifacts:");
            Set<MavenProject> rebuildProjects = mavenSession.getProjects().stream()
                    .filter(changed::contains)
                    .flatMap(p -> getAllDependents(mavenSession.getProjects(), p).stream())
                    .flatMap(this::ifMakeUpstreamGetDependencies)
                    .collect(Collectors.toSet());
            mavenSession.getProjects().retainAll(rebuildProjects);
        }
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
        if (configuration.makeUpstream) {
            return getAllDependencies(mavenSession.getProjects(), mavenProject).stream();
        } else {
            return Stream.of(mavenProject);
        }
    }

    private Set<MavenProject> getAllDependencies(List<MavenProject> projects, MavenProject project) {
        Set<MavenProject> dependencies = project.getDependencies().stream().map(d -> convert(projects, d)).filter(Optional::isPresent).map(Optional::get)
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

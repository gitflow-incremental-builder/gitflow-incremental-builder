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
import java.util.Set;

@Singleton
public class UnchangedProjectsRemover {

    @Inject private Properties properties;
    @Inject private Logger logger;
    @Inject private ChangedProjects changedProjects;
    @Inject private MavenSession mavenSession;

    public void act() throws GitAPIException, IOException {
        if (!properties.enabled) {
            logger.info("GIB is disabled.");
        } else {
            Set<MavenProject> changed = changedProjects.get();
            printDelimiter();
            logProjects(changed, "Changed Artifacts:");
            Set<MavenProject> rebuildProjects = new HashSet<>();
            for (MavenProject mavenProject: mavenSession.getProjects()) {
                if (changed.contains(mavenProject)) {
                    rebuildProjects.addAll(getAllDependents(mavenSession.getProjects(), mavenProject));
                }
            }
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
            for (Dependency dependency : possibleDependent.getDependencies()) {
                if (equals(project, dependency)) {
                    result.addAll(getAllDependents(projects, possibleDependent));
                }
            }
            if (project.equals(possibleDependent.getParent())) {
                result.addAll(getAllDependents(projects, possibleDependent));
            }
        }
        return result;
    }

    private boolean equals(MavenProject project, Dependency dependency) {
        return dependency.getArtifactId().equals(project.getArtifactId())
                && dependency.getGroupId().equals(project.getGroupId())
                && dependency.getVersion().equals(project.getVersion());
    }
}

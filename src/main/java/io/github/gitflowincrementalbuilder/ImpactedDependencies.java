package io.github.gitflowincrementalbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;

@Singleton
@Named
class ImpactedDependencies {

    private Logger logger = LoggerFactory.getLogger(ImpactedDependencies.class);

    public Set<MavenProject> get(Configuration config) {
        if (config.impactedDependenciesFrom.isEmpty()) {
            return Collections.emptySet();
        }

        Path impactedDepsFile = config.impactedDependenciesFrom.get();

        if (!Files.exists(impactedDepsFile)) {
            logger.warn("Impacted dependencies file does not exist: {}", impactedDepsFile);
            return Collections.emptySet();
        }

        try {
            // Read all GAVs from the file
            Set<String> impactedGAVs = Files.readAllLines(impactedDepsFile, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toSet());

            if (impactedGAVs.isEmpty()) {
                logger.info("Impacted dependencies file is empty: {}", impactedDepsFile);
                return Collections.emptySet();
            }

            logger.info("Found {} impacted dependencies from file: {}", impactedGAVs.size(), impactedDepsFile);
            impactedGAVs.forEach(gav -> logger.debug("  - {}", gav));

            // Find all projects that have transitive dependencies matching the impacted GAVs
            Set<MavenProject> affectedProjects = new HashSet<>();
            for (MavenProject project : config.mavenSession.getProjects()) {
                if (hasTransitiveDependency(project, impactedGAVs)) {
                    affectedProjects.add(project);
                    logger.debug("Project {} has transitive dependency to impacted GAV", project.getId());
                }
            }

            logger.info("Found {} projects with transitive dependencies to impacted GAVs", affectedProjects.size());
            return affectedProjects;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read impacted dependencies from " + impactedDepsFile, e);
        }
    }

    private boolean hasTransitiveDependency(MavenProject project, Set<String> impactedGAVs) {
        List<Dependency> allDependencies = project.getModel().getDependencies();
        if (allDependencies == null) {
            return false;
        }

        // Check direct and transitive dependencies
        for (Dependency dep : allDependencies) {
            String depGAV = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
            if (impactedGAVs.contains(depGAV)) {
                return true;
            }
        }

        // Also check artifacts in the project's dependency artifacts (which includes transitive)
        if (project.getArtifacts() != null) {
            for (org.apache.maven.artifact.Artifact artifact : project.getArtifacts()) {
                String artifactGAV = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
                if (impactedGAVs.contains(artifactGAV)) {
                    return true;
                }
            }
        }

        return false;
    }
}



package com.vackosar.gitflowincrementalbuild.control;

import com.google.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class Modules {

    public Map<Path, MavenProject> createPathMap(MavenSession session) {
        return session.getProjects().stream()
                .collect(Collectors.toMap(Modules::getPath, project -> project));
    }

    private static Path getPath(MavenProject project) {
        return project.getBasedir().toPath().normalize().toAbsolutePath();
    }
}

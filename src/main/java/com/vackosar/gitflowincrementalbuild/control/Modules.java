package com.vackosar.gitflowincrementalbuild.control;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
public class Modules {

    public Map<Path, MavenProject> createPathMap(MavenSession session) {
        return session.getAllProjects().stream()
                .collect(Collectors.toMap(Modules::getPath, project -> project));
    }

    private static Path getPath(MavenProject project) {
        return project.getBasedir().toPath().normalize().toAbsolutePath();
    }
}

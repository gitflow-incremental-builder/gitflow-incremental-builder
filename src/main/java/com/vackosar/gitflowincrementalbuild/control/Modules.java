package com.vackosar.gitflowincrementalbuild.control;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

@Singleton
@Named
public class Modules {

    /**
     * Returns all projects of the given session, mapped by their project base directory.
     * In very special setups, multiple modules can exists in the same directory and so the mapping value is a list of projects.
     */
    public Map<Path, List<MavenProject>> createPathMap(MavenSession session) {
        return session.getAllProjects().stream().collect(
                Collectors.toMap(
                        Modules::getPath,
                        Collections::singletonList,
                        (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).collect(Collectors.toList())));
    }

    private static Path getPath(MavenProject project) {
        return project.getBasedir().toPath().normalize().toAbsolutePath();
    }
}

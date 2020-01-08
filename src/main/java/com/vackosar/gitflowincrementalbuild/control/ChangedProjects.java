package com.vackosar.gitflowincrementalbuild.control;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Named
public class ChangedProjects {

    private Logger logger = LoggerFactory.getLogger(ChangedProjects.class);

    @Inject private DifferentFiles differentFiles;
    @Inject private MavenSession mavenSession;
    @Inject private Modules modules;

    public Set<MavenProject> get() throws GitAPIException, IOException {
        return differentFiles.get().stream()
                .map(path -> findProject(path))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private MavenProject findProject(Path diffPath) {
        Map<Path, MavenProject> map = modules.createPathMap(mavenSession);
        Path path = diffPath;
        while (path != null && ! map.containsKey(path)) {
            path = path.getParent();
        }
        if (path != null) {
            logger.debug("Changed file: {}", diffPath);
            return map.get(path);
        } else {
            logger.warn("Changed file outside build project: {}", diffPath);
            return null;
        }
    }
}

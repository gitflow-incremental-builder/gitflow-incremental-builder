package com.vackosar.gitflowincrementalbuild.control;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ChangedModules {

    @Inject private Logger logger;
    @Inject private DifferentFiles differentFiles;
    @Inject private MavenSession mavenSession;
    @Inject private Modules modules;

    public Set<MavenProject> set() throws GitAPIException, IOException {
        return differentFiles.list().stream()
                .map(path -> findProject(path, mavenSession))
                .filter(project -> project != null)
                .collect(Collectors.toSet());
    }

    private MavenProject findProject(Path diffPath, MavenSession mavenSession) {
        Map<Path, MavenProject> map = modules.createPathMap(mavenSession);
        Path path = diffPath;
        while (path != null && ! map.containsKey(path)) {
            path = path.getParent();
        }
        if (path != null) {
            return map.get(path);
        } else {
            logger.warn("File changed outside build project: " + diffPath);
            return null;
        }
    }
}

package com.vackosar.gitflowincrementalbuild.control;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ChangedModules {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject private DifferentFiles differentFiles;
    @Inject private ModuleDirs moduleDirs;

    public Set<Path> list(Path pom) throws GitAPIException, IOException {
        return differentFiles.list().stream()
                .map(path -> findModulePath(path, pom))
                .filter(modulePath -> modulePath != null)
                .map(nonNullModulePath -> pom.getParent().relativize(nonNullModulePath))
                .collect(Collectors.toSet());
    }

    private Path findModulePath(Path diffPath, Path pom) {
        final List<Path> moduleDirs = this.moduleDirs.list(pom);
        Path path = diffPath;
        while (path != null && ! moduleDirs.contains(path)) {
            path = path.getParent();
        }
        if (path == null) {
            logger.warn("File changed outside build project: " + diffPath);
        }
        return path;
    }

}

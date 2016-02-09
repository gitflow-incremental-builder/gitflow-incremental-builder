package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ChangedModulesLister {

    private Logger logger = Logger.getLogger(getClass().getCanonicalName());;

    public Set<Path> act(Path pom) throws GitAPIException, IOException {
        Path canonicalPom = pom.toAbsolutePath().toRealPath().normalize();
        final Set<Path> changedModuleDirs = new HashSet<>();
        final Set<Path> diffs = new DiffLister().act();
        final List<Path> moduleDirs = new ModuleDirLister().act(canonicalPom);
        for (final Path diffPath: diffs) {
            Path path = diffPath;
            while (path != null && ! moduleDirs.contains(path)) {
                path = path.getParent();
            }
            if (path == null || ! moduleDirs.contains(path)) {
                logger.warning("Change outside build project: " + diffPath);
            } else if (! changedModuleDirs.stream().map(canonicalPom.getParent()::resolve).anyMatch(path::startsWith)) {
                changedModuleDirs.add(canonicalPom.getParent().relativize(path));
            }
        }
        return changedModuleDirs;
    }
}

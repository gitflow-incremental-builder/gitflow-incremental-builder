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
        final List<Path> diffs = new DiffLister().act();
        final List<Path> moduleDirs = new ModuleDirLister().act(canonicalPom);
        for (final Path diffPath: diffs) {
            Path path = diffPath;
            while (path != null && ! moduleDirs.contains(path)) {
                path = path.getParent();
            }
            if (moduleDirs.contains(path)) {
                changedModuleDirs.add(canonicalPom.getParent().relativize(path));
            } else {
                logger.warning("Change outside build project: " + diffPath);
            }
        }
        return changedModuleDirs;
    }
}

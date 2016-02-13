package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Singleton
public class ChangedModulesLister {

    private Logger logger = Logger.getLogger(getClass().getCanonicalName());

    @Inject private DiffLister diffLister;
    @Inject private ModuleDirLister moduleDirLister;

    public Set<Path> act(Path pom) throws GitAPIException, IOException {
        Path canonicalPom = pom.toAbsolutePath().toRealPath().normalize();
        final Set<Path> changedModuleDirs = new HashSet<>();
        final Set<Path> diffs = diffLister.act();
        final List<Path> moduleDirs = moduleDirLister.act(canonicalPom);
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

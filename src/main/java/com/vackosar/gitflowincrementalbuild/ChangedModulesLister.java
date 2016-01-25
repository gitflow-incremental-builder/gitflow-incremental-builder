package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ChangedModulesLister {

    private Logger logger = Logger.getLogger(getClass().getCanonicalName());;

    public List<Path> act(Path pom) throws GitAPIException, IOException {
        final List<Path> changedModuleDirs = new ArrayList<>();
        final List<Path> diffs = new DiffLister().act();
        final List<Path> moduleDirs = new ModuleDirLister().act(pom);
        for (final Path diffPath: diffs) {
            Path path = diffPath;
            while (path != null && ! moduleDirs.contains(path)) {
                path = path.getParent();
            }
            if (moduleDirs.contains(path)) {
                final Path name = path.getName(path.getNameCount()-1);
                changedModuleDirs.add(name);
            } else {
                logger.warning("Change outside build project: " + diffPath);
            }
        }
        return changedModuleDirs;
    }
}

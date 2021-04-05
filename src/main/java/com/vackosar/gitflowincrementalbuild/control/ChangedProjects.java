package com.vackosar.gitflowincrementalbuild.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;

@Singleton
@Named
public class ChangedProjects {

    /**
     * Key of the {@link MavenProject#setContextValue(String, Object) context value} this class sets for each returned project. A value of {@link Boolean#TRUE}
     * means that only changes in the {@code src/test} part of the module were detected.
     */
    public static final String CTX_TEST_ONLY = ChangedProjects.class.getName() + "#TEST-ONLY";

    private Logger logger = LoggerFactory.getLogger(ChangedProjects.class);

    @Inject private DifferentFiles differentFiles;
    @Inject private Modules modules;

    public Set<MavenProject> get(Configuration config) throws GitAPIException, IOException {
        Map<Path, MavenProject> modulesPathMap = modules.createPathMap(config.mavenSession);
        return differentFiles.get(config).stream()
                .map(path -> findProject(path, modulesPathMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private MavenProject findProject(Path diffPath, Map<Path, MavenProject> modulesPathMap) {
        Path path = diffPath;
        // Files.exist() to spot changes in non-reactor module (path will then yield a null changedReactorProject).
        // Without this check, the changed path would be wrongly mapped to the "closest" reactor module (which might not have changed at all!).
        while (path != null && !modulesPathMap.containsKey(path) && !Files.exists(path.resolve("pom.xml"))) {
            path = path.getParent();
        }
        if (path == null) {
            logger.warn("Ignoring changed file outside build project: {}", diffPath);
            return null;
        }
        MavenProject changedReactorProject = modulesPathMap.get(path);
        if (changedReactorProject == null) {
            logger.warn("Ignoring changed file in non-reactor module: {}", diffPath);
            return null;
        }
        logger.debug("Changed file: {}", diffPath);
        Boolean testOnlyFlag = (Boolean) changedReactorProject.getContextValue(CTX_TEST_ONLY);
        if (!Boolean.FALSE.equals(testOnlyFlag)) {
            changedReactorProject.setContextValue(CTX_TEST_ONLY, diffPath.startsWith(path.resolve("src").resolve("test")));
        }
        return changedReactorProject;
    }
}

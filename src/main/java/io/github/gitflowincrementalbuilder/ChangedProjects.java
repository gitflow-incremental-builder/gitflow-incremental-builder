package io.github.gitflowincrementalbuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.jgit.DifferentFiles;
import io.github.gitflowincrementalbuilder.jgit.GitProvider;

@Singleton
@Named
class ChangedProjects {

    /**
     * Key of the {@link MavenProject#setContextValue(String, Object) context value} this class sets for each returned project. A value of {@link Boolean#TRUE}
     * means that only changes in the {@code src/test} part of the module were detected.
     */
    public static final String CTX_TEST_ONLY = ChangedProjects.class.getName() + "#TEST-ONLY";

    private Logger logger = LoggerFactory.getLogger(ChangedProjects.class);

    @Inject private DifferentFiles differentFiles;
    @Inject private Modules modules;
    @Inject private GitProvider gitProvider;

    public Set<MavenProject> get(Configuration config) {
        Map<Path, List<MavenProject>> modulesPathMap = modules.createPathMap(config.mavenSession);
        Path projectRoot = gitProvider.getProjectRoot(config);
        return differentFiles.get(config).stream()
                .flatMap(path -> findProject(path, modulesPathMap, projectRoot).stream())
                .collect(Collectors.toSet());
    }

    private List<MavenProject> findProject(Path diffPath, Map<Path, List<MavenProject>> modulesPathMap, Path projectRoot) {
        // Strip src/* subpath (if present) to make sure that embedded (test) projects contribute
        // to the "change state" of containing reactor module instead of considering them as separate (non-reactor) modules.
        Path path = stripSrcSubpath(diffPath, projectRoot);
        // Files.exist() to spot changes in non-reactor module (path will then yield a null changedReactorProject).
        // Without this check, the changed path would be wrongly mapped to the "closest" reactor module (which might not have changed at all!).
        while (path != null && !modulesPathMap.containsKey(path) && !pomXmlExistsIn(path)) {
            path = path.getParent();
        }
        if (path == null) {
            logger.debug("Ignoring changed file outside build project: {}", diffPath);
            return Collections.emptyList();
        }
        List<MavenProject> changedReactorProjects = modulesPathMap.get(path);
        if (changedReactorProjects == null) {
            logger.debug("Ignoring changed file in non-reactor module: {}", diffPath);
            return Collections.emptyList();
        }
        logger.debug("Changed file: {}", diffPath);
        for (MavenProject changedReactorProject : changedReactorProjects) {
            Boolean testOnlyFlag = (Boolean) changedReactorProject.getContextValue(CTX_TEST_ONLY);
            if (!Boolean.FALSE.equals(testOnlyFlag)) {
                changedReactorProject.setContextValue(CTX_TEST_ONLY, diffPath.startsWith(path.resolve("src").resolve("test")));
            }
        }
        return changedReactorProjects;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
               justification = "Extremely unlikely that getFileName() or getRoot() will return null here.")
    private Path stripSrcSubpath(Path path, Path projectRoot) {
        int elementIndex = 0;
        Path relativePath = projectRoot.relativize(path);
        for (Path element : relativePath) {
            // note: just "src" is good enough for 99.9% of projects,
            // for the rest we'd need to evaluate (test) compile source roots and resources of the "closest" module
            if (relativePath.getParent() != null && element.getFileName().toString().equals("src")) {
                Path shortenedPath = elementIndex == 0 ? projectRoot : projectRoot.resolve(relativePath.subpath(0, elementIndex));
                // if there is no pom.xml next to src we must not consider it a "Maven src" folder
                if (pomXmlExistsIn(shortenedPath)) {
                    return shortenedPath;
                }
            }
            elementIndex++;
        }
        return path;
    }

    private boolean pomXmlExistsIn(Path path) {
        return Files.exists(path.resolve("pom.xml"));
    }
}

package io.github.gitflowincrementalbuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

@Singleton
@Named
class Modules {

    /**
     * Returns all projects of the given session, mapped by their project base directory.
     * In very special setups, multiple modules can exists in the same directory and so the mapping value is a list of projects.
     *
     * @param session the Maven session
     * @return project(s) mapped by path
     */
    public Map<Path, List<MavenProject>> createPathMap(MavenSession session) {
        return session.getAllProjects().stream().collect(
                Collectors.toMap(
                        Modules::getPath,
                        Collections::singletonList,
                        (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).collect(Collectors.toList())));
    }

    /**
     * Collects the declared modules of the given project (recursively) into the given result set.
     * Modules are resolved via {@link org.apache.maven.model.Model#getModules()} (not via directory containment),
     * so that "out of tree" modules like {@code ../other} are covered.
     * Only {@code candidates} are considered, so projects that are not part of it (e.g. deselected ones) are never added.
     *
     * @param project the project whose modules shall be collected
     * @param candidates the projects that may be returned as modules, usually {@link MavenSession#getProjects()}
     * @return the (recursively) found modules, never containing {@code project} itself unless it is part of a module cycle
     */
    static Set<MavenProject> collectModulesOf(MavenProject project, Collection<MavenProject> candidates) {
        Set<MavenProject> result = new LinkedHashSet<>();
        collectModulesOf(project, candidates, result);
        return result;
    }

    private static void collectModulesOf(MavenProject project, Collection<MavenProject> candidates, Set<MavenProject> result) {
        for (String module : project.getModel().getModules()) {
            Path modulePath = getPath(project).resolve(module).normalize();
            candidates.stream()
                    .filter(candidate -> isModuleLocation(candidate, modulePath))
                    .filter(result::add)    // also protects against cycles
                    .forEach(child -> collectModulesOf(child, candidates, result));
        }
    }

    private static boolean isModuleLocation(MavenProject candidate, Path modulePath) {
        if (modulePath.equals(getPath(candidate))) {
            return true;
        }
        // module might point directly to a pom file, but that is not the norm, so check this last
        File pomFile = candidate.getFile();
        return pomFile != null && modulePath.equals(pomFile.toPath().toAbsolutePath().normalize());
    }

    private static Path getPath(MavenProject project) {
        return project.getBasedir().toPath().toAbsolutePath().normalize();
    }
}
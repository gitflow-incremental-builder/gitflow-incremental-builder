package io.github.gitflowincrementalbuilder;

import java.util.Collection;

import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.graph.DefaultProjectDependencyGraph;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Configuration.RebuildProjectDependencyGraphMode;
import io.github.gitflowincrementalbuilder.config.Property;

public class ProjectDependencyGraphFactory {

    static final int REBUILD_DURATION_THRESHOLD_MS = 1000;

    private static Logger logger = LoggerFactory.getLogger(ProjectDependencyGraphFactory.class);

    public static ProjectDependencyGraph createGraph(Collection<MavenProject> projects, Configuration config, boolean forceCreation) {
        if (!forceCreation && config.rebuildProjectDependencyGraphMode == RebuildProjectDependencyGraphMode.OFF) {
            return config.mavenSession.getProjectDependencyGraph();
        }

        var start = System.currentTimeMillis();
        try {
            try {
                return new DefaultProjectDependencyGraph(projects);
            } catch (NoClassDefFoundError err) {
                // cannot use DPDG in maven < 3.8.8 (https://issues.apache.org/jira/browse/MNG-6972) so use our own copy
                return new Maven38DefaultDependencyGraph(projects);
            }
        } catch (CycleDetectedException | DuplicateProjectException e) {
            if (forceCreation) {
                throw new IllegalStateException("Failed to build project dependency graph for allProjects", e);
            }
            logger.warn("Failed to rebuild project dependency graph, falling back to session graph. "
                    + "Projects added or modified by previous extensions will not be picked up!", e);
            return config.mavenSession.getProjectDependencyGraph();
        } finally {
            var duration = System.currentTimeMillis() - start;
            if (!forceCreation && config.rebuildProjectDependencyGraphMode == RebuildProjectDependencyGraphMode.AUTO
                    && duration >= REBUILD_DURATION_THRESHOLD_MS) {
                logger.info("Graph creation for {} projects took {}ms, which is a considerable overhead. "
                        + "If there are no other Maven extensions in place that add or modify projects, "
                        + "this rebuild can be switched off via property '{}' to save some time.",
                        projects.size(), duration, Property.rebuildProjectDependencyGraphMode.prefixedName());
            } else {
                logger.debug("Graph creation for {} projects took {}ms", projects.size(), duration);
            }
        }
    }

}

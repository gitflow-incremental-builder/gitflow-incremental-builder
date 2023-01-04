package io.github.gitflowincrementalbuilder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.TypeAwareExpressionEvaluator;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;

@Singleton
@Named
class DownstreamCalculator {

    private static final String PCKG_POM = "pom";
    private static final String SCOPE_TEST = "test";
    private static final String TEST_JAR = "test-jar";
    private static final String TEST_JAR_DEFAULT_CLASSIFIER = "tests";

    private final Map<String, Set<MavenProject>> downstreamCache = new HashMap<>();
    private final Map<String, Set<String>> testJarClassifiersCache = new HashMap<>();
    private ProjectDependencyGraph graph;

    private Logger logger = LoggerFactory.getLogger(DownstreamCalculator.class);

    public Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project, Configuration config) {
        if (graph == null) {
            if (config.mavenSession.getProjects().size() != config.mavenSession.getAllProjects().size()) {
                // The reactor has been trimmed/filtered, most likely because of -pl and so the default ProjectDependencyGraph
                // will not give us any downstream dependencies for modules that are not part of the trimmed reactor.
                // Therefore we need to create a separate graph that contains all modules.
                // Note: Due to https://issues.apache.org/jira/browse/MNG-6972 we cannot use DefaultDependencyGraph directly.
                try {
                    graph = new Maven38DefaultDependencyGraph(config.mavenSession.getAllProjects());
                } catch (CycleDetectedException | DuplicateProjectException e) {
                    throw new IllegalStateException(e); // extremely unlikely
                }
            } else {
                graph = config.mavenSession.getProjectDependencyGraph();
            }
        }
        boolean testOnly = Boolean.TRUE.equals(project.getContextValue(ChangedProjects.CTX_TEST_ONLY));
        // idea: if testOnly, try to map actual changes to test-jar inclusions/exclusions (if present) and bail out if no match
        // possible issue: a file that is not part of a test-jar might contribute to a (generated) file that _is_ part of the test-jar
        return streamProjectWithDownstreamProjects(project, testOnly, config);
    }

    private Stream<MavenProject> streamProjectWithDownstreamProjects(MavenProject project, boolean testOnly, Configuration config) {
        final String cacheKey = project.hashCode() + "_" + testOnly;
        // note: not using computeIfAbsent() here since it would break recursive invocations with ConcurrentModificationException
        Set<MavenProject> downstream = downstreamCache.get(cacheKey);
        if (downstream != null) {
            return downstream.stream();
        }
        downstream = new LinkedHashSet<>();
        downstream.add(project);

        List<MavenProject> unfilteredDownstream = graph.getDownstreamProjects(project, false);
        if (!unfilteredDownstream.isEmpty()) {
            for (MavenProject downstreamProj : unfilteredDownstream) {
                ActualDependentState state = getActualDependentState(downstreamProj, project, testOnly);
                logger.debug("{} -> {} :: {} [testOnly={}]", downstreamProj.getArtifactId(), project.getArtifactId(), state, testOnly);
                if (state != ActualDependentState.NONE) {
                    streamProjectWithDownstreamProjects(downstreamProj, state == ActualDependentState.TEST, config)
                            .filter(proj -> isDownstreamModuleNotExcluded(proj, config))
                            .forEach(downstream::add);
                }
            }
        }
        if (PCKG_POM.equals(project.getPackaging())) {    // performance hint: bomArtifactIdRegex or similar could speed things up
            downstream.addAll(findBOMDownstreamProjects(project, downstream, config));
        }
        downstreamCache.put(cacheKey, downstream);

        return downstream.stream();
    }

    public void clearCache() {
        graph = null;
        downstreamCache.clear();
        testJarClassifiersCache.clear();
    }

    private ActualDependentState getActualDependentState(MavenProject downstreamProject, MavenProject upstreamProject, boolean upstreamTestOnly) {
        if (PCKG_POM.equals(upstreamProject.getPackaging()) && upstreamProject.equals(downstreamProject.getParent())) {
            return ActualDependentState.MAIN;
        }
        Map<String, Dependency> depsByClassifier = downstreamProject.getDependencies().stream()
                .filter(dep -> dep.getArtifactId().equals(upstreamProject.getArtifactId())
                        && dep.getGroupId().equals(upstreamProject.getGroupId())
                        && dep.getVersion().equals(upstreamProject.getVersion()))
                .collect(Collectors.toMap(this::getClassifier, Function.identity(), (a, b) -> a));

        if (depsByClassifier.isEmpty()) {
            // most likely a plugin dependency, not worth checking in detail (for now)
            logger.debug("Could not find dependency to {} in {}", upstreamProject.getId(), downstreamProject.getId());
            return ActualDependentState.MAIN;
        }

        if (upstreamTestOnly) {
            Set<String> upstreamTestJarClassifiers = findTestJarClassifiers(upstreamProject);
            Map<String, List<Dependency>> testJarDepsByScope = depsByClassifier.entrySet().stream()
                    .filter(e -> upstreamTestJarClassifiers.contains(e.getKey()))
                    .map(Entry::getValue)
                    .collect(Collectors.groupingBy(this::getScopeOrCompile));    // ensure non-null key via getScopeOrCompile()
            if (testJarDepsByScope.isEmpty()) {
                return ActualDependentState.NONE;
            } else {
                if (testJarDepsByScope.size() == 1
                        && testJarDepsByScope.containsKey(SCOPE_TEST)
                        && testJarDepsByScope.get(SCOPE_TEST).stream().noneMatch(this::isMinimalDependency)) {
                    return ActualDependentState.TEST;
                }
                return ActualDependentState.MAIN;
            }
            // note: don't care for unlikely "provided" scope here (until somebody reports an actual issue)
        } else {
            return depsByClassifier.values().stream().allMatch(d -> SCOPE_TEST.equals(d.getScope()) && !isMinimalDependency(d))
                    ? ActualDependentState.TEST
                    : ActualDependentState.MAIN;
        }
    }

    // Minimal/"symbolic" dependency for build order, e.g.:
    // <dependency>
    //     <groupId>io.quarkus</groupId>
    //     <artifactId>quarkus-agroal-deployment</artifactId>
    //     <version>${project.version}</version>
    //     <type>pom</type>
    //     <scope>test</scope>
    //     <exclusions>
    //         <exclusion>
    //             <groupId>*</groupId>
    //             <artifactId>*</artifactId>
    //         </exclusion>
    //     </exclusions>
    // </dependency>
    private boolean isMinimalDependency(Dependency dependency) {
        // assumption: GAV already checked before calling this method
        List<Exclusion> exclusions = dependency.getExclusions();
        if (exclusions.size() != 1) {
            return false;
        }
        Exclusion excl = exclusions.get(0);
        return "*".equals(excl.getGroupId()) && "*".equals(excl.getArtifactId())
                && PCKG_POM.equals(dependency.getType()) && SCOPE_TEST.equals(dependency.getScope());
    }

    private String getClassifier(Dependency dep) {
        String classifier = dep.getClassifier();
        if (classifier == null) {
            classifier = TEST_JAR.equals(dep.getType()) ? TEST_JAR_DEFAULT_CLASSIFIER : "";
        }
        return classifier;
    }

    private String getScopeOrCompile(Dependency dep) {
        String scope = dep.getScope();
        return scope != null ? scope : "compile";
    }

    private Set<String> findTestJarClassifiers(MavenProject project) {
        return testJarClassifiersCache.computeIfAbsent(String.valueOf(project.hashCode()), ignored -> project.getBuildPlugins().stream()
                .filter(p -> "maven-jar-plugin".equals(p.getArtifactId()))
                .flatMap(p -> p.getExecutions().stream().filter(e -> e.getGoals().contains(TEST_JAR)))
                .map(e -> (Xpp3Dom) e.getConfiguration())
                .map(d -> Optional.ofNullable(d.getChild("classifier")).map(Xpp3Dom::getValue).orElse(TEST_JAR_DEFAULT_CLASSIFIER))
                .collect(Collectors.toUnmodifiableSet()));
    }

    private boolean isDownstreamModuleNotExcluded(MavenProject proj, Configuration config) {
        return !config.excludeDownstreamModulesPackagedAs.contains(proj.getPackaging());
    }

    private Set<MavenProject> findBOMDownstreamProjects(MavenProject potentialBOMProject, Set<MavenProject> downstream, Configuration config) {
        return config.mavenSession.getAllProjects().stream()    // "All" is crucial to properly handle de-selected BOM case (with dsph)
                .filter(proj -> !downstream.contains(proj)) // optimization
                .filter(proj -> isDownstreamModuleNotExcluded(proj, config))
                .filter(proj -> importsBOM(proj, potentialBOMProject, config))
                .flatMap(proj -> streamProjectWithDownstreamProjects(proj, false, config)) // (indirect) recursion!
                .filter(config.mavenSession.getProjects()::contains)   // skip projects not part of the actual reactor (see getAllProjects() further up)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Boolean importsBOM(MavenProject project, MavenProject potentialBOMProject, Configuration config) {
        return Optional.ofNullable(project.getOriginalModel())    // >original< model is crucial since BOM deps are gone in effective model
                .map(Model::getDependencyManagement)
                .map(depMgtm -> depMgtm.getDependencies().stream().anyMatch(dep -> isBOMImport(dep, potentialBOMProject, project, config)))
                .orElse(false);
    }

    private boolean isBOMImport(Dependency dependency, MavenProject potentialBOMProject, MavenProject depDefiningProject, Configuration config) {
        LazyExpressionEvaluator evaluator = new LazyExpressionEvaluator(config.mavenSession, depDefiningProject);
        return Objects.equals(evaluator.evaluate(dependency.getType()), PCKG_POM)
                && Objects.equals(evaluator.evaluate(dependency.getScope()), Artifact.SCOPE_IMPORT)
                && Objects.equals(evaluator.evaluate(dependency.getArtifactId()), potentialBOMProject.getArtifactId())
                && Objects.equals(evaluator.evaluate(dependency.getGroupId()), potentialBOMProject.getGroupId())
                && Objects.equals(evaluator.evaluate(dependency.getVersion()), potentialBOMProject.getVersion());
    }

    private enum ActualDependentState {
        MAIN,
        TEST,
        NONE;
    }

    private static class LazyExpressionEvaluator {

        private static final Logger LOGGER = LoggerFactory.getLogger(LazyExpressionEvaluator.class);

        private final MavenSession session;
        private final MavenProject project;

        private TypeAwareExpressionEvaluator evaluator;

        public LazyExpressionEvaluator(MavenSession session, MavenProject project) {
            this.session = session;
            this.project = project;
        }

        public String evaluate(String expression) {
            if (expression == null || !expression.contains("${")) {
                return expression;
            }
            if (evaluator == null) {
                // set project on cloned session otherwise properties might be resolved from a more or less unrelated project
                MavenSession clonedSession = session.clone();
                clonedSession.setCurrentProject(project);
                // there is also a ctor without MojoExecution parameter but evaluate() will then fail with a NPE (sic!)
                // see also: https://issues.apache.org/jira/browse/MNG-6982
                evaluator = new PluginParameterExpressionEvaluator(clonedSession, new MojoExecution(new MojoDescriptor()));
            }
            try {
                return evaluator.evaluate(expression, String.class).toString();
            } catch (ExpressionEvaluationException e) {
                LOGGER.warn("Failed to evaluate: " + expression, e);
                return expression;
            }
        }
    }
}

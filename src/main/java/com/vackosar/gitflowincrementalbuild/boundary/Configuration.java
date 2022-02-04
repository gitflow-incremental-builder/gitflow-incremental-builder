package com.vackosar.gitflowincrementalbuild.boundary;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.Property.ValueWithOriginContext;

public class Configuration {

    public static final String PLUGIN_KEY = "io.github.gitflow-incremental-builder:gitflow-incremental-builder";

    public final MavenSession mavenSession;
    public final MavenProject currentProject;

    public final boolean help;
    public final boolean disable;
    public final Optional<Predicate<String>> disableIfBranchMatches;
    public final Optional<Predicate<String>> disableIfReferenceBranchMatches;

    public final boolean disableBranchComparison;
    public final String referenceBranch;
    public final boolean fetchReferenceBranch;
    public final String baseBranch;
    public final boolean fetchBaseBranch;
    public final boolean compareToMergeBase;
    public final boolean uncommitted;
    public final boolean untracked;
    public final Optional<Predicate<String>> skipIfPathMatches;
    public final Optional<Predicate<String>> excludePathsMatching;
    public final Optional<Predicate<String>> includePathsMatching;

    public final boolean buildAll;
    public final boolean buildAllIfNoChanges;
    public final boolean buildDownstream;
    public final BuildUpstreamMode buildUpstreamMode;
    public final boolean skipTestsForUpstreamModules;
    public final Map<String, String> argsForUpstreamModules;
    public final List<Pattern> forceBuildModules;
    public final Map<Pattern, Pattern> forceBuildModulesConditionally;
    public final List<String> excludeDownstreamModulesPackagedAs;
    public final boolean disableSelectedProjectsHandling;

    public final boolean failOnMissingGitDir;
    public final boolean failOnError;
    public final Optional<Path> logImpactedTo;

    private Logger logger = LoggerFactory.getLogger(Configuration.class);

    public Configuration(MavenSession session) {
        this.mavenSession = session;
        this.currentProject = findCurrentProject(session);

        Properties[] properties = getProperties(currentProject, logger);
        Properties projectProperties = properties[0];
        Properties pluginProperties = properties[1];

        help = Boolean.parseBoolean(Property.help.getValue(pluginProperties, projectProperties));
        disable = Boolean.parseBoolean(Property.disable.getValue(pluginProperties, projectProperties));
        if (disable) { // abort parsing any other config properties if not enabled at all
            disableIfBranchMatches = null;
            disableIfReferenceBranchMatches = null;

            // change detection config

            disableBranchComparison = false;
            referenceBranch = null;
            fetchReferenceBranch = false;
            baseBranch = null;
            fetchBaseBranch = false;
            compareToMergeBase = false;
            uncommitted = false;
            untracked = false;
            skipIfPathMatches = null;
            excludePathsMatching = null;
            includePathsMatching = null;

            // build config

            buildAll = false;
            buildAllIfNoChanges = false;
            buildDownstream = false;
            buildUpstreamMode = null;
            skipTestsForUpstreamModules = false;

            argsForUpstreamModules = null;

            forceBuildModules = null;
            forceBuildModulesConditionally = null;

            excludeDownstreamModulesPackagedAs = null;

            disableSelectedProjectsHandling = false;

            // error handling config

            failOnMissingGitDir = false;
            failOnError = false;
            logImpactedTo = null;

            return;
        }

        Property.checkProperties(pluginProperties, projectProperties);

        disableIfBranchMatches = compileOptionalPatternPredicate(Property.disableIfBranchMatches, pluginProperties, projectProperties);
        disableIfReferenceBranchMatches = compileOptionalPatternPredicate(Property.disableIfReferenceBranchMatches, pluginProperties, projectProperties);

        // change detection config

        disableBranchComparison = Boolean.parseBoolean(Property.disableBranchComparison.getValue(pluginProperties, projectProperties));
        referenceBranch = Property.referenceBranch.getValue(pluginProperties, projectProperties);
        fetchReferenceBranch = Boolean.parseBoolean(Property.fetchReferenceBranch.getValue(pluginProperties, projectProperties));
        baseBranch = Property.baseBranch.getValue(pluginProperties, projectProperties);
        fetchBaseBranch = Boolean.parseBoolean(Property.fetchBaseBranch.getValue(pluginProperties, projectProperties));
        compareToMergeBase = Boolean.parseBoolean(Property.compareToMergeBase.getValue(pluginProperties, projectProperties));
        uncommitted = Boolean.parseBoolean(Property.uncommitted.getValue(pluginProperties, projectProperties));
        untracked = Boolean.parseBoolean(Property.untracked.getValue(pluginProperties, projectProperties));
        skipIfPathMatches = compileOptionalPatternPredicate(Property.skipIfPathMatches, pluginProperties, projectProperties);
        excludePathsMatching = compileOptionalPatternPredicate(Property.excludePathsMatching, pluginProperties, projectProperties);
        includePathsMatching = compileOptionalPatternPredicate(Property.includePathsMatching, pluginProperties, projectProperties);

        // build config

        buildAll = Boolean.parseBoolean(Property.buildAll.getValue(pluginProperties, projectProperties));
        buildAllIfNoChanges = Boolean.parseBoolean(Property.buildAllIfNoChanges.getValue(pluginProperties, projectProperties));
        buildDownstream = isBuildStreamActive(
                Property.buildDownstream, pluginProperties, projectProperties, session, MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);
        buildUpstreamMode = parseBuildUpstreamMode(session, pluginProperties, projectProperties);
        skipTestsForUpstreamModules = Boolean.parseBoolean(Property.skipTestsForUpstreamModules.getValue(pluginProperties, projectProperties));

        argsForUpstreamModules = parseDelimited(Property.argsForUpstreamModules.getValue(pluginProperties, projectProperties), " ")
                .map(Configuration::keyValueStringToEntry)
                .collect(collectingAndThen(toLinkedMap(), Collections::unmodifiableMap));

        Map<String, String> forceBuildModulesMap = parseDelimited(Property.forceBuildModules.getValue(pluginProperties, projectProperties), ",")
                .map(Configuration::keyValueStringToEntry)
                .collect(toLinkedMap());
        forceBuildModules = forceBuildModulesMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(entry -> compilePattern(entry.getKey(), Property.forceBuildModules))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
        forceBuildModulesConditionally = forceBuildModulesMap.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(collectingAndThen(
                        toLinkedMap(
                                entry -> compilePattern(entry.getKey(), Property.forceBuildModules),
                                entry -> compilePattern(entry.getValue(), Property.forceBuildModules)),
                        Collections::unmodifiableMap));

        excludeDownstreamModulesPackagedAs = parseDelimited(Property.excludeDownstreamModulesPackagedAs.getValue(pluginProperties, projectProperties), ",")
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        disableSelectedProjectsHandling = Boolean.parseBoolean(Property.disableSelectedProjectsHandling.getValue(pluginProperties, projectProperties));

        // error handling config

        failOnMissingGitDir = Boolean.parseBoolean(Property.failOnMissingGitDir.getValue(pluginProperties, projectProperties));
        failOnError = Boolean.parseBoolean(Property.failOnError.getValue(pluginProperties, projectProperties));
        logImpactedTo = Property.logImpactedTo.getValueOpt(pluginProperties, projectProperties).map(Paths::get);
    }

    /**
     * Returns whether or not the given make behaviour is active for the given session.
     *
     * @param expectedMakeBehavior one of {@link MavenExecutionRequest#REACTOR_MAKE_DOWNSTREAM} or {@link MavenExecutionRequest#REACTOR_MAKE_UPSTREAM}
     * @param session the session providing the request
     * @return whether the given behaviour (or {@link MavenExecutionRequest#REACTOR_MAKE_BOTH}) is active or not
     */
    public static boolean isMakeBehaviourActive(String expectedMakeBehavior, MavenSession session) {
        String actualMakeBehavior = session.getRequest().getMakeBehavior();
        return expectedMakeBehavior.equals(actualMakeBehavior) || MavenExecutionRequest.REACTOR_MAKE_BOTH.equals(actualMakeBehavior);
    }

    private static MavenProject findCurrentProject(MavenSession session) {
        // MavenSession.getCurrentProject() does not return the correct value in some cases,
        // see: https://issues.apache.org/jira/browse/MNG-6979
        MavenProject currentProject = session.getCurrentProject();
        if (currentProject == null || !currentProject.isExecutionRoot()) {
            currentProject = session.getProjects().stream()
                    .filter(MavenProject::isExecutionRoot)
                    .findAny()
                    .orElse(currentProject);
        }
        // design note: could theoretically call session.setCurrentProject(...) here, but this seems too risky
        return currentProject;
    }

    private static Properties[] getProperties(MavenProject currentProject, Logger logger) {
        if (currentProject != null) {
            return new Properties[] { currentProject.getProperties(), getPluginProperties(currentProject) };
        } else {
            logger.warn("gitflow-incremental-builder could not parse configuration due to missing 'current' project in the MavenSession.");
            Properties fakeProjectProperties = new Properties();
            fakeProjectProperties.put(Property.disable.prefixedName(), Boolean.TRUE.toString());
            return new Properties[] { fakeProjectProperties, new Properties() };
        }
    }

    private static Properties getPluginProperties(MavenProject mavenProject) {
        return Optional.ofNullable(mavenProject.getPlugin(PLUGIN_KEY))
                .map(plugin -> (Xpp3Dom) plugin.getConfiguration())
                .map(Xpp3Dom::getChildren)
                .filter(children -> children.length > 0)
                .map(children -> Arrays.stream(children)
                        .collect(Collectors.toMap(Xpp3Dom::getName, Xpp3Dom::getValue, (a, b) -> a, Properties::new)))
                .orElseGet(Properties::new);
    }

    private static BuildUpstreamMode parseBuildUpstreamMode(MavenSession session, Properties pluginProperties, Properties projectProperties) {
        if (!isBuildStreamActive(Property.buildUpstream, pluginProperties, projectProperties, session, MavenExecutionRequest.REACTOR_MAKE_UPSTREAM)) {
            return BuildUpstreamMode.NONE;
        }
        ValueWithOriginContext propertyValue = Property.buildUpstreamMode.getValueWithOriginContext(pluginProperties, projectProperties);
        try {
            return BuildUpstreamMode.valueOf(propertyValue.value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GIB property '" + propertyValue.originName + "' defines an invalid mode: " + propertyValue, e);
        }
    }

    private static boolean isBuildStreamActive(Property property, Properties pluginProperties, Properties projectProperties, MavenSession session,
            String expectedMakeBehavior) {
        ValueWithOriginContext propertyValue = property.getValueWithOriginContext(pluginProperties, projectProperties);
        switch (propertyValue.value) {
            case "derived":
                return isMakeBehaviourActive(expectedMakeBehavior, session);
            case "always":
            case "true":
                return true;
            case "never":
            case "false":
                return false;
            default:
                throw new IllegalArgumentException("GIB property '" + propertyValue.originName + "' defines an invalid value: " + propertyValue);
        }
    }

    private static Stream<String> parseDelimited(String value, String delimiter) {
        return value.isEmpty()
                ? Stream.empty()
                : Arrays.stream(value.split(delimiter))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty());
    }

    private static Map.Entry<String, String> keyValueStringToEntry(String pair) {
        int indexOfDelim = pair.indexOf('=');
        return indexOfDelim > 0
                ? new AbstractMap.SimpleEntry<>(pair.substring(0, indexOfDelim), pair.substring(indexOfDelim + 1))
                : new AbstractMap.SimpleEntry<>(pair, "");
    }

    private static Collector<Entry<String, String>, ?, LinkedHashMap<String, String>> toLinkedMap() {
        return toLinkedMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private static <IN, OUT> Collector<Entry<IN, IN>, ?, LinkedHashMap<OUT, OUT>> toLinkedMap(
            Function<Entry<IN, IN>, OUT> keyMapper, Function<Entry<IN, IN>, OUT> valueMapper) {
        return Collectors.toMap(keyMapper, valueMapper, (a, b) -> a, LinkedHashMap::new);
    }

    private static Pattern compilePattern(String patternString, Property property) {
        try {
            return Pattern.compile(patternString);
        } catch (PatternSyntaxException e) {
            // just using prefixedName() because it is too laborious get ValueWithOriginContext here
            throw new IllegalArgumentException("GIB property '" + property.prefixedName() + "' defines an invalid pattern string", e);
        }
    }

    private static Optional<Predicate<String>> compileOptionalPatternPredicate(Property property, Properties pluginProperties, Properties projectProperties) {
        return property.getValueOpt(pluginProperties, projectProperties)
                .map(patternString -> compilePattern(patternString, property))
                .map(Pattern::asMatchPredicate);
    }

    public static enum BuildUpstreamMode {
        NONE,
        CHANGED,
        IMPACTED;
    }
}

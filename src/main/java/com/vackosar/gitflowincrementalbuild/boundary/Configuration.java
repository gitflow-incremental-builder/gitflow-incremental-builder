package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class Configuration {

    static final String PLUGIN_KEY = "com.vackosar.gitflowincrementalbuilder:gitflow-incremental-builder";

    // statically cached properties of plugin (if present)
    private static Properties cachedPluginProperties;

    public final Optional<Predicate<String>> disableIfBranchRegex;

    public final boolean disableBranchComparison;
    public final String referenceBranch;
    public final boolean fetchReferenceBranch;
    public final String baseBranch;
    public final boolean fetchBaseBranch;
    public final boolean useJschAgentProxy;
    public final boolean compareToMergeBase;
    public final boolean uncommited;
    public final boolean untracked;
    public final Optional<Predicate<String>> excludePathRegex;
    public final Optional<Predicate<String>> includePathRegex;

    public final boolean buildAll;
    public final boolean buildAllIfNoChanges;
    public final boolean buildDownstream;
    public final BuildUpstreamMode buildUpstreamMode;
    public final boolean skipTestsForUpstreamModules;
    public final Map<String, String> argsForUpstreamModules;
    public final List<Pattern> forceBuildModules;
    public final List<String> excludeDownstreamModulesPackagedAs;

    public final boolean failOnMissingGitDir;
    public final boolean failOnError;
    public final Optional<Path> logImpactedTo;

    private Configuration(MavenSession session) {
        Properties projectProperties = getProjectProperties(session);
        checkProperties(projectProperties);

        Properties pluginProperties = getPluginProperties(session);

        disableIfBranchRegex = compileOptionalPatternPredicate(Property.disableIfBranchRegex, pluginProperties, projectProperties);

        // change detection config

        disableBranchComparison = Boolean.valueOf(Property.disableBranchComparison.getValue(pluginProperties, projectProperties));
        referenceBranch = Property.referenceBranch.getValue(pluginProperties, projectProperties);
        fetchReferenceBranch = Boolean.valueOf(Property.fetchReferenceBranch.getValue(pluginProperties, projectProperties));
        baseBranch = Property.baseBranch.getValue(pluginProperties, projectProperties);
        fetchBaseBranch = Boolean.valueOf(Property.fetchBaseBranch.getValue(pluginProperties, projectProperties));
        useJschAgentProxy = Boolean.valueOf(Property.useJschAgentProxy.getValue(pluginProperties, projectProperties));
        compareToMergeBase = Boolean.valueOf(Property.compareToMergeBase.getValue(pluginProperties, projectProperties));
        uncommited = Boolean.valueOf(Property.uncommited.getValue(pluginProperties, projectProperties));
        untracked = Boolean.valueOf(Property.untracked.getValue(pluginProperties, projectProperties));
        excludePathRegex = compileOptionalPatternPredicate(Property.excludePathRegex, pluginProperties, projectProperties);
        includePathRegex = compileOptionalPatternPredicate(Property.includePathRegex, pluginProperties, projectProperties);

        // build config

        buildAll = Boolean.valueOf(Property.buildAll.getValue(pluginProperties, projectProperties));
        buildAllIfNoChanges = Boolean.valueOf(Property.buildAllIfNoChanges.getValue(pluginProperties, projectProperties));
        buildDownstream = isBuildStreamActive(
                Property.buildDownstream, pluginProperties, projectProperties, session, MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);
        buildUpstreamMode = parseBuildUpstreamMode(session, pluginProperties, projectProperties);
        skipTestsForUpstreamModules = Boolean.valueOf(Property.skipTestsForUpstreamModules.getValue(pluginProperties, projectProperties));

        argsForUpstreamModules = parseDelimited(Property.argsForUpstreamModules.getValue(pluginProperties, projectProperties), " ")
                .map(Configuration::keyValueStringToEntry)
                .collect(collectingAndThen(toLinkedMap(), Collections::unmodifiableMap));

        forceBuildModules = parseDelimited(Property.forceBuildModules.getValue(pluginProperties, projectProperties), ",")
                .map(str -> compilePattern(str, Property.forceBuildModules))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        excludeDownstreamModulesPackagedAs = parseDelimited(Property.excludeDownstreamModulesPackagedAs.getValue(pluginProperties, projectProperties), ",")
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        // error handling config

        failOnMissingGitDir = Boolean.valueOf(Property.failOnMissingGitDir.getValue(pluginProperties, projectProperties));
        failOnError = Boolean.valueOf(Property.failOnError.getValue(pluginProperties, projectProperties));
        logImpactedTo = Property.logImpactedTo.getValueOpt(pluginProperties, projectProperties).map(Paths::get);

        cachedPluginProperties = null;
    }

    /**
     * Returns the value for {@link Property#enabled} without initializing all the other configuration fields to abort quickly without any additional overhead.
     *
     * @param session the current session
     * @return whether or not GIB is enabled or not
     */
    public static boolean isEnabled(MavenSession session) {
        boolean enabled = Boolean.parseBoolean(Property.enabled.getValue(getPluginProperties(session), getProjectProperties(session)));
        if (!enabled) {
            cachedPluginProperties = null;
        }
        return enabled;
    }

    /**
     * Returns the value for {@link Property#help} without initializing all the other configuration fields (help can be requested even if
     * {@link #isEnabled(MavenSession)} returns {@code false}).
     *
     * @param session the current session
     * @return whether or not to print GIB help
     */
    public static boolean isHelpRequested(MavenSession session) {
        return Boolean.valueOf(Property.help.getValue(getPluginProperties(session), getProjectProperties(session)));
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

    private static Properties getProjectProperties(MavenSession session) {
        return session.getTopLevelProject().getProperties();
    }

    private static Properties getPluginProperties(MavenSession session) {
        if (cachedPluginProperties == null) {
            Plugin plugin = session.getTopLevelProject().getPlugin(PLUGIN_KEY);
            if (plugin != null) {
                cachedPluginProperties = new Properties();
                Arrays.stream(((Xpp3Dom) plugin.getConfiguration()).getChildren())
                        .forEach(child -> cachedPluginProperties.put(child.getName(), child.getValue()));
            } else {
                cachedPluginProperties = new Properties();
            }
        }
        return cachedPluginProperties;
    }

    private static void checkProperties(Properties projectProperties) {
        Set<String> availablePropertyNames = Arrays.stream(Property.values())
                .flatMap(p -> p.allNames().stream())
                .collect(Collectors.toSet());
        String invalidPropertyNames = Stream.concat(System.getProperties().keySet().stream(), projectProperties.keySet().stream())
                .distinct()
                .map(k -> (String) k)
                .filter(k -> k.startsWith(Property.PREFIX) && !availablePropertyNames.contains(k))
                .collect(Collectors.joining("\n\t"));
        if (!invalidPropertyNames.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Invalid GIB properties found:%n\t%s%nAllowed properties:%n%s", invalidPropertyNames, Property.exemplifyAll()));
        }
    }

    private static BuildUpstreamMode parseBuildUpstreamMode(MavenSession session, Properties pluginProperties, Properties projectProperties) {
        if (!isBuildStreamActive(Property.buildUpstream, pluginProperties, projectProperties, session, MavenExecutionRequest.REACTOR_MAKE_UPSTREAM)) {
            return BuildUpstreamMode.NONE;
        }
        try {
            String propertyValue = Optional.ofNullable(Property.buildUpstreamMode.getValue(pluginProperties, projectProperties)).orElse("");
            return BuildUpstreamMode.valueOf(propertyValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("GIB property " + Property.buildUpstreamMode.fullOrShortName() + " defines an invalid mode", e);
        }
    }

    private static boolean isBuildStreamActive(Property property, Properties pluginProperties, Properties projectProperties, MavenSession session,
            String expectedMakeBehavior) {
        switch (property.getValue(pluginProperties, projectProperties)) {
            case "derived":
                return isMakeBehaviourActive(expectedMakeBehavior, session);
            case "always":
            case "true":
                return true;
            case "never":
            case "false":
                return false;
            default:
                throw new IllegalArgumentException(
                        "GIB property " + property.fullOrShortName() + " defines an invalid value: " + property.getValue(pluginProperties, projectProperties));
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
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new);
    }

    private static Pattern compilePattern(String patternString, Property property) {
        try {
            return Pattern.compile(patternString);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("GIB property " + property.fullOrShortName() + " defines an invalid pattern string", e);
        }
    }

    private static Optional<Predicate<String>> compileOptionalPatternPredicate(Property property, Properties pluginProperties, Properties projectProperties) {
        return property.getValueOpt(pluginProperties, projectProperties)
                .map(patternString -> compilePattern(patternString, property))
                .map(Pattern::asPredicate);
    }

    public static enum BuildUpstreamMode {
        NONE,
        CHANGED,
        IMPACTED;
    }

    @Singleton
    @Named
    public static class Provider implements javax.inject.Provider<Configuration> {

        private final MavenSession mavenSession;

        private Configuration configuration;

        @Inject
        public Provider(MavenSession mavenSession) {
            this.mavenSession = mavenSession;
        }

        /**
         * Returns a {@link Configuration} instance which is constructed when first called. Subsequent calls will return the same instance.
         *
         * @return a {@link Configuration} instance
         */
        @Override
        public Configuration get() {
            if (configuration == null) {
                cachedPluginProperties = null;
                configuration = new Configuration(mavenSession);
            }
            return configuration;
        }
    }
}

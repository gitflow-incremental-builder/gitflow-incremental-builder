package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

public class Configuration {

    private static final List<String> alsoMakeBehaviours = Arrays.asList(
            MavenExecutionRequest.REACTOR_MAKE_UPSTREAM, MavenExecutionRequest.REACTOR_MAKE_BOTH);

    public final Optional<Path> key;
    public final boolean disableBranchComparison;
    public final String referenceBranch;
    public final String baseBranch;
    public final boolean uncommited;
    public final boolean untracked;
    public final boolean makeUpstream;
    public final boolean skipTestsForNotImpactedModules;
    public final Map<String, String> argsForNotImpactedModules;
    public final boolean buildAll;
    public final List<Pattern> forceBuildModules;
    public final List<String> excludeTransitiveModulesPackagedAs;
    public final boolean compareToMergeBase;
    public final boolean fetchBaseBranch;
    public final boolean fetchReferenceBranch;
    public final Predicate<String> excludePathRegex;
    public final boolean failOnMissingGitDir;
    public final boolean failOnError;

    private Configuration(MavenSession session) {
        Properties projectProperties = session.getTopLevelProject().getProperties();
        checkProperties(projectProperties);

        key = parseKey(session, projectProperties);
        disableBranchComparison = Boolean.valueOf(Property.disableBranchComparison.getValue(projectProperties));
        referenceBranch = Property.referenceBranch.getValue(projectProperties);
        baseBranch = Property.baseBranch.getValue(projectProperties);
        uncommited = Boolean.valueOf(Property.uncommited.getValue(projectProperties));
        untracked = Boolean.valueOf(Property.untracked.getValue(projectProperties));
        makeUpstream = alsoMakeBehaviours.contains(session.getRequest().getMakeBehavior());
        skipTestsForNotImpactedModules = Boolean.valueOf(Property.skipTestsForNotImpactedModules.getValue(projectProperties));

        argsForNotImpactedModules = parseDelimited(Property.argsForNotImpactedModules.getValue(projectProperties), " ")
                .map(Configuration::keyValueStringToEntry)
                .collect(collectingAndThen(toLinkedMap(), Collections::unmodifiableMap));

        buildAll = Boolean.valueOf(Property.buildAll.getValue(projectProperties));

        forceBuildModules = parseDelimited(Property.forceBuildModules.getValue(projectProperties), ",")
                .map(str -> compilePattern(str, Property.forceBuildModules))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        excludeTransitiveModulesPackagedAs = parseDelimited(Property.excludeTransitiveModulesPackagedAs.getValue(projectProperties), ",")
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        compareToMergeBase = Boolean.valueOf(Property.compareToMergeBase.getValue(projectProperties));
        fetchReferenceBranch = Boolean.valueOf(Property.fetchReferenceBranch.getValue(projectProperties));
        fetchBaseBranch = Boolean.valueOf(Property.fetchBaseBranch.getValue(projectProperties));
        excludePathRegex = compilePattern(Property.excludePathRegex, projectProperties).asPredicate();
        failOnMissingGitDir = Boolean.valueOf(Property.failOnMissingGitDir.getValue(projectProperties));
        failOnError = Boolean.valueOf(Property.failOnError.getValue(projectProperties));
    }

    /**
     * Returns the value for {@link Property#enabled} without initializing all the other configuration fields to abort quickly without any additional overhead.
     *
     * @param session the current session
     * @return whether or not GIB is enabled or not
     */
    public static boolean isEnabled(MavenSession session) {
        return Boolean.valueOf(Property.enabled.getValue(session.getTopLevelProject().getProperties()));
    }

    private static void checkProperties(Properties projectProperties) {
        Set<String> availablePropertyNames = Arrays.stream(Property.values())
                .map(Property::fullName)
                .collect(Collectors.toSet());
        String invalidPropertyNames = Stream.concat(System.getProperties().keySet().stream(), projectProperties.keySet().stream())
                .distinct()
                .map(k -> (String) k)
                .filter(k -> k.startsWith(Property.PREFIX) && !availablePropertyNames.contains(k))
                .collect(Collectors.joining("\n\t"));
        if (!invalidPropertyNames.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Invalid GIB properties found:\n\t%s\nAllowed properties:\n%s", invalidPropertyNames, Property.exemplifyAll()));
        }
    }

    private static Optional<Path> parseKey(MavenSession session, Properties projectProperties) {
        String keyOptionValue = Property.repositorySshKey.getValue(projectProperties);
        if (keyOptionValue != null && ! keyOptionValue.isEmpty()) {
            Path pomDir = session.getCurrentProject().getBasedir().toPath();
            return Optional.of(pomDir.resolve(keyOptionValue).toAbsolutePath().normalize());
        } else {
            return Optional.empty();
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
            throw new IllegalArgumentException("GIB property " + property.fullName() + " defines an invalid pattern string", e);
        }
    }

    private static Pattern compilePattern(Property property, Properties projectProperties) {
        return compilePattern(property.getValue(projectProperties), property);
    }

    @Singleton
    @Named("gib.configurationProvider")
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
                configuration = new Configuration(mavenSession);
            }
            return configuration;
        }
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class Configuration {

    private static final List<String> alsoMakeBehaviours = Arrays.asList(
            MavenExecutionRequest.REACTOR_MAKE_UPSTREAM, MavenExecutionRequest.REACTOR_MAKE_BOTH);

    public final boolean enabled;
    public final Optional<Path> key;
    public final boolean disableBranchComparison;
    public final String referenceBranch;
    public String baseBranch;
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

    @Inject
    public Configuration(MavenSession session) {
        checkProperties();

        enabled = Boolean.valueOf(Property.enabled.getValue());
        key = parseKey(session);
        disableBranchComparison = Boolean.valueOf(Property.disableBranchComparison.getValue());
        referenceBranch = Property.referenceBranch.getValue();
        baseBranch = Property.baseBranch.getValue();
        uncommited = Boolean.valueOf(Property.uncommited.getValue());
        untracked = Boolean.valueOf(Property.untracked.getValue());
        makeUpstream = alsoMakeBehaviours.contains(session.getRequest().getMakeBehavior());
        skipTestsForNotImpactedModules = Boolean.valueOf(Property.skipTestsForNotImpactedModules.getValue());

        argsForNotImpactedModules = parseDelimited(Property.argsForNotImpactedModules.getValue(), " ")
                .map(Configuration::keyValueStringToEntry)
                .collect(collectingAndThen(toLinkedMap(), Collections::unmodifiableMap));

        buildAll = Boolean.valueOf(Property.buildAll.getValue());

        forceBuildModules = parseDelimited(Property.forceBuildModules.getValue(), ",")
                .map(str -> compilePattern(str, Property.forceBuildModules))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        excludeTransitiveModulesPackagedAs = parseDelimited(Property.excludeTransitiveModulesPackagedAs.getValue(), ",")
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));

        compareToMergeBase = Boolean.valueOf(Property.compareToMergeBase.getValue());
        fetchReferenceBranch = Boolean.valueOf(Property.fetchReferenceBranch.getValue());
        fetchBaseBranch = Boolean.valueOf(Property.fetchBaseBranch.getValue());
        excludePathRegex = compilePattern(Property.excludePathRegex).asPredicate();
        failOnMissingGitDir = Boolean.valueOf(Property.failOnMissingGitDir.getValue());
    }

    private static void checkProperties() {
        Set<String> availablePropertyNames = Arrays.stream(Property.values())
                .map(Property::fullName)
                .collect(Collectors.toSet());
        String invalidPropertyNames = System.getProperties().keySet().stream()
                .map(k -> (String) k)
                .filter(k -> k.startsWith(Property.PREFIX) && !availablePropertyNames.contains(k))
                .collect(Collectors.joining("\n\t"));
        if (!invalidPropertyNames.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Invalid GIB properties found:\n\t%s\nAllowed properties:\n%s", invalidPropertyNames, Property.exemplifyAll()));
        }
    }

    private static Optional<Path> parseKey(MavenSession session) {
        String keyOptionValue = Property.repositorySshKey.getValue();
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

    private static Pattern compilePattern(Property property) {
        return compilePattern(property.getValue(), property);
    }
}

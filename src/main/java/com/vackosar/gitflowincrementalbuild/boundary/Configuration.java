package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public final List<String> forceBuildModules;
    public final boolean compareToMergeBase;
    public final boolean fetchBaseBranch;
    public final boolean fetchReferenceBranch;
    public final Predicate<String> excludePathRegex;
    public final boolean failOnMissingGitDir;

    @Inject
    public Configuration(MavenSession session) throws IOException {
        try {
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
            argsForNotImpactedModules = Collections.unmodifiableMap(
                    parseSpaceDelimitedArgs(Property.argsForNotImpactedModules.getValue()));
            buildAll = Boolean.valueOf(Property.buildAll.getValue());
            forceBuildModules = Collections.unmodifiableList(
                    parseDelimited(Property.forceBuildModules.getValue(), ","));
            compareToMergeBase = Boolean.valueOf(Property.compareToMergeBase.getValue());
            fetchReferenceBranch = Boolean.valueOf(Property.fetchReferenceBranch.getValue());
            fetchBaseBranch = Boolean.valueOf(Property.fetchBaseBranch.getValue());
            excludePathRegex = Pattern.compile(Property.excludePathRegex.getValue()).asPredicate();
            failOnMissingGitDir = Boolean.valueOf(Property.failOnMissingGitDir.getValue());
        } catch (MavenExecutionException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<Path> parseKey(MavenSession session) throws IOException {
        Path pomDir = session.getCurrentProject().getBasedir().toPath();
        String keyOptionValue = Property.repositorySshKey.getValue();
        if (keyOptionValue != null && ! keyOptionValue.isEmpty()) {
            return Optional.of(pomDir.resolve(keyOptionValue).toAbsolutePath().toRealPath().normalize());
        } else {
            return Optional.empty();
        }
    }

    private static List<String> parseDelimited(String value, String delimiter) {
        return value.isEmpty()
                ? Collections.emptyList()
                : Arrays.stream(value.split(delimiter))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
    }

    private static Map<String, String> parseSpaceDelimitedArgs(String value) {
        return parseDelimited(value, " ").stream()
                .map(Configuration::keyValueStringToEntry)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static Map.Entry<String, String> keyValueStringToEntry(String pair) {
        int indexOfDelim = pair.indexOf('=');
        return indexOfDelim > 0
                ? new AbstractMap.SimpleEntry<>(pair.substring(0, indexOfDelim), pair.substring(indexOfDelim + 1))
                : new AbstractMap.SimpleEntry<>(pair, "");
    }

    private static void checkProperties() throws MavenExecutionException {
        try {
            System.getProperties().entrySet().stream().map(Map.Entry::getKey)
                    .filter(o -> o instanceof String).map(o -> (String) o)
                    .filter(s -> s.startsWith(Property.PREFIX))
                    .map(s -> s.replaceFirst(Property.PREFIX, ""))
                    .forEach(Property::valueOf);
        } catch (IllegalArgumentException e) {
            throw new MavenExecutionException("Invalid invalid GIB property found. Allowed properties: \n" + Property.exemplifyAll(), e);
        }
    }
}

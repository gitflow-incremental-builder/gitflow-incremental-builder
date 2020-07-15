package com.vackosar.gitflowincrementalbuild.control;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GIB configuration properties. This is exposed as a "fake" plugin mojo/goal to generate a plugin descriptor which is picked up by maven-help-plugin and IDEs
 * to provide auto-completion etc. Do not try to execute this fake goal!
 */
@Mojo(name = "config-do-not-execute", threadSafe = true)
public enum Property {
    /**
     * Logs the available properties etc.
     */
    @Parameter(defaultValue = "false", alias = "h")
    help("false", "h", true),
    /**
     * Can be used to disable this extension temporarily or permanently.
     */
    @Parameter(defaultValue = "true", alias = "e")
    enabled("true", "e", true),
    /**
     * Can be used to disable this extension on certain branches.
     */
    @Parameter(defaultValue = "", alias = "dibr")
    disableIfBranchRegex("", "dibr"),

    /**
     * Disables the comparison between baseBranch and referenceBranch.
     */
    @Parameter(defaultValue = "false", alias = "dbc")
    disableBranchComparison("false", "dbc", true),
    /**
     * The branch to compare baseBranch to.
     */
    @Parameter(defaultValue = "refs/remotes/origin/develop", alias = "rb")
    referenceBranch("refs/remotes/origin/develop", "rb"),
    /**
     * Fetches the referenceBranch from the remote repository.
     */
    @Parameter(defaultValue = "false", alias = "frb")
    fetchReferenceBranch("false", "frb", true),
    /**
     * The branch that is compared to referenceBranch.
     */
    @Parameter(defaultValue = "HEAD", alias = "bb")
    baseBranch("HEAD", "bb"),
    /**
     * Fetches the baseBranch from the remote repository
     */
    @Parameter(defaultValue = "false", alias = "fbb")
    fetchBaseBranch("false", "fbb", true),
    /**
     * Can be used to disable the usage of jsch-agent-proxy when fetching via SSH.
     */
    @Parameter(defaultValue = "true", alias = "ujap")
    useJschAgentProxy("true", "ujap"),
    /**
     * Controls whether or not to the merge-base mechanism to compare the branches.
     */
    @Parameter(defaultValue = "true", alias = "ctmb")
    compareToMergeBase("true", "ctmb", true),
    /**
     * Detects changed files that have not yet been committed.
     */
    @Parameter(defaultValue = "true", alias = "uc")
    uncommited("true", "uc", true),
    /**
     * Detects files that are not yet tracked by git.
     */
    @Parameter(defaultValue = "true", alias = "ut")
    untracked("true", "ut", true),
    /**
     * Can be used to exclude certain changed files from being detected as changed, reducing the number of modules to build.
     */
    @Parameter(defaultValue = "", alias = "epr")
    excludePathRegex("", "epr"),
    /**
     * Can be used to include only certain changed files from being detected as changed, reducing the number of modules to build.
     */
    @Parameter(defaultValue = "", alias = "ipr")
    includePathRegex("", "ipr"),

    /**
     * Builds all modules, including upstream modules.
     */
    @Parameter(defaultValue = "false", alias = "ba")
    buildAll("false", "ba", true),
    /**
     * Can be used to active buildAll if no changes are detected (instead of just building the root module with goal validate).
     */
    @Parameter(defaultValue = "false", alias = "bainc")
    buildAllIfNoChanges("false", "bainc", true),
    /**
     * Controls whether or not to build downstream modules.
     */
    @Parameter(defaultValue = "always", alias = "bd")
    buildDownstream("always", "bd", true),
    /**
     * Controls whether or not to build upstream modules.
     */
    @Parameter(defaultValue = "derived", alias = "bu")
    buildUpstream("derived", "bu", true),
    /**
     * This property controls which upstream modules to build.
     */
    @Parameter(defaultValue = "changed", alias = "bum")
    buildUpstreamMode("changed", "bum"),
    /**
     * This property disables the compilation/execution of tests for upstream modules.
     */
    @Parameter(defaultValue = "false", alias = "stfum")
    skipTestsForUpstreamModules("false", "stfum", true),
    /**
     * This property allows adding arbitrary arguments/properties for upstream modules to futher reduce overhead.
     */
    @Parameter(defaultValue = "", alias = "afum")
    argsForUpstreamModules("", "afum"),
    /**
     * Defines artifact ids of modules to build forcibly.
     */
    @Parameter(defaultValue = "", alias = "fbm")
    forceBuildModules("", "fbm"),
    /**
     * Defines the packaging (e.g. jar) of modules that depend on changed modules but shall not be built.
     */
    @Parameter(defaultValue = "", alias = "edmpa")
    excludeDownstreamModulesPackagedAs("", "edmpa") {
        @Override
        public String deprecatedName() {
            return "excludeTransitiveModulesPackagedAs";
        }
    },

    /**
     * Controls whether or not to fail on missing .git directory.
     */
    @Parameter(defaultValue = "true", alias = "fomgd")
    failOnMissingGitDir("true", "fomgd", true),
    /**
     * Controls whether or not to fail on any error.
     */
    @Parameter(defaultValue = "true", alias = "foe")
    failOnError("true", "foe", true),
    /**
     * Defines an optional logfile which GIB shall write all "impacted" modules to.
     */
    @Parameter(defaultValue = "", alias = "lit")
    logImpactedTo("", "lit");

    public static final String PREFIX = "gib.";

    private static final Logger LOGGER = LoggerFactory.getLogger(Property.class);

    private final String prefixedName;
    private final String prefixedShortName;

    private final List<String> nameCandidatesForSystemProperties;
    private final List<String> nameCandidatesForPluginProperties;
    private final List<String> nameCandidatesForProjectProperties;

    private final String defaultValue;

    private final boolean mapEmptyValueToTrue;

    Property(String defaultValue, String unprefixedShortName) {
        this(defaultValue, unprefixedShortName, false);
    }

    Property(String defaultValue, String unprefixedShortName, boolean mapNoValueToTrue) {
        this.prefixedName = PREFIX + name();
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.prefixedShortName = PREFIX + unprefixedShortName;

        this.nameCandidatesForSystemProperties = Stream.of(prefixedName, prefixedShortName, deprecatedPrefixedName())
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        this.nameCandidatesForPluginProperties = Stream.of(name(), deprecatedName())
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        this.nameCandidatesForProjectProperties = Stream.of(prefixedName, deprecatedPrefixedName())
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        this.mapEmptyValueToTrue = mapNoValueToTrue;
    }

    private String exemplify() {
        return String.format(
                "%-83s<!-- or -D%-13s -->", "<" + prefixedName + ">" + defaultValue + "</" + prefixedName + ">", prefixedShortName + "=...");
    }

    /**
     * @return the {@value #PREFIX}-prefixed full name.
     */
    public String prefixedName() {
        return prefixedName;
    }

    /**
     * @return the {@value #PREFIX}-prefixed short name.
     */
    public String prefixedShortName() {
        return prefixedShortName;
    }

    /**
     * @return the deprecated unprefixed name or {@code null} if there is no such deprecated name for this property
     */
    public String deprecatedName() {
        // might be overridden by specific enum instances
        return null;
    }

    /**
     * @return the deprecated prefixed name or {@code null} if there is no such deprecated name for this property
     */
    public final String deprecatedPrefixedName() {
        return Optional.ofNullable(deprecatedName()).map(PREFIX::concat).orElse(null);
    }

    public ValueWithOriginContext getValueWithOriginContext(Properties pluginProperties, Properties projectProperties) {
        Optional<ValueWithOriginContext> valueWithName = getValueWithOriginContext(nameCandidatesForSystemProperties, System.getProperties(), "system");
        if (!valueWithName.isPresent()) {
            valueWithName = getValueWithOriginContext(nameCandidatesForPluginProperties, pluginProperties, "plugin");
        }
        if (!valueWithName.isPresent()) {
            valueWithName = getValueWithOriginContext(nameCandidatesForProjectProperties, projectProperties, "project");
        }
        ValueWithOriginContext finalValueWithOriginContext = valueWithName.orElseGet(() -> new ValueWithOriginContext(defaultValue, name(), "default"));
        LOGGER.debug("{}", finalValueWithOriginContext);
        return finalValueWithOriginContext;
    }

    public String getValue(Properties pluginProperties, Properties projectProperties) {
        return getValueWithOriginContext(pluginProperties, projectProperties).value;
    }

    public Optional<String> getValueOpt(Properties pluginProperties, Properties projectProperties) {
        final String value = getValue(pluginProperties, projectProperties);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private Optional<ValueWithOriginContext> getValueWithOriginContext(List<String> nameCandidates, Properties properties, String propertiesDesc) {
        return nameCandidates.stream()
                .map(nameCandidate -> getValueWithOriginContext(nameCandidate, properties, propertiesDesc))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private ValueWithOriginContext getValueWithOriginContext(String name, Properties properties, String propertiesDesc) {
        String value = properties.getProperty(name);
        if (value == null) {
            return null;
        }
        boolean prefixed = name.startsWith(PREFIX);
        String deprecatedName = prefixed ? deprecatedPrefixedName() : deprecatedName();
        if (name.equals(deprecatedName)) {
            LOGGER.warn("{} has been replaced with {} and will be removed in an upcoming release. Please adjust your configuration!",
                    deprecatedName, prefixed ? prefixedName : name());
        }
        if (mapEmptyValueToTrue && value.isEmpty()) {
            value = "true";
        }
        return new ValueWithOriginContext(value, name, propertiesDesc);
    }

    public static void checkProperties(Properties pluginProperties, Properties projectProperties) {
        String errorDetails = "";

        String invalidSystemPropertyNames = checkProperties(System.getProperties(), true, p -> p.nameCandidatesForSystemProperties);
        if (!invalidSystemPropertyNames.isEmpty()) {
            errorDetails += "\n\tinvalid system properties:\n\t\t" + invalidSystemPropertyNames;
        }

        String invalidPluginPropertyNames = checkProperties(pluginProperties, false, p -> p.nameCandidatesForPluginProperties);
        if (!invalidPluginPropertyNames.isEmpty()) {
            errorDetails += "\n\tinvalid plugin properties:\n\t\t" + invalidPluginPropertyNames;
        }

        String invalidProjectPropertyNames = checkProperties(projectProperties, true, p -> p.nameCandidatesForProjectProperties);
        if (!invalidProjectPropertyNames.isEmpty()) {
            errorDetails += "\n\tinvalid project properties:\n\t\t" + invalidProjectPropertyNames;
        }

        if (!errorDetails.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Invalid GIB properties found:%s%nAllowed properties:%n%s", errorDetails, Property.exemplifyAll()));
        }
    }

    private static String checkProperties(Properties properties, boolean prefixed, Function<Property, List<String>> availableNamesProvider) {
        Set<String> allAvailableNames = Arrays.stream(Property.values())
                .flatMap(p -> availableNamesProvider.apply(p).stream())
                .collect(Collectors.toSet());

        return properties.isEmpty() ? "" : properties.keySet().stream()
                .map(k -> (String) k)
                .filter(k -> (!prefixed || k.startsWith(Property.PREFIX)) && !allAvailableNames.contains(k))
                .collect(Collectors.joining("\n\t\t"));
    }

    public static String exemplifyAll() {
        StringBuilder builder = new StringBuilder();
        builder.append("<properties>\n");
        for (Property value :Property.values()) {
            builder.append("    ").append(value.exemplify()).append("\n");
        }
        builder.append("</properties>\n");
        return builder.toString();
    }

    public static class ValueWithOriginContext {
        public final String value;
        public final String originName;
        public final String originProperties;

        protected ValueWithOriginContext(String value, String originName, String originProperties) {
            this.value = value;
            this.originName = originName;
            this.originProperties = originProperties;
        }

        @Override
        public String toString() {
            return "From " + originProperties + ": " + originName + "=" + value;
        }
    }
}

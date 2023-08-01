package io.github.gitflowincrementalbuilder.config;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GIB configuration properties. This is exposed as a "fake" plugin mojo/goal to generate a plugin descriptor which is picked up by maven-help-plugin and IDEs
 * to provide auto-completion etc. Do not try to execute this fake goal!
 * <p>
 * Note: This enum is auto-enriched with maven plugin annotations via
 * {@link io.github.gitflowincrementalbuilder.mojo.MojoParametersGeneratingByteBuddyPlugin MojoParametersGeneratingByteBuddyPlugin}.<br>
 * Each enum instance <b>must</b> have a short JavaDoc description that should match the respective first sentence in {@code README.md}.
 * </p>
 */
public enum Property {
    /**
     * Logs the available properties etc.
     */
    help("false", "h", true),
    /**
     * Can be used to disable this extension temporarily or permanently.
     */
    disable("false", "d", true),
    /**
     * Can be used to disable this extension on certain branches.
     */
    disableIfBranchMatches("", "dibm"),
    /**
     * Can be used to disable this extension for certain reference branches.
     */
    disableIfReferenceBranchMatches("", "dirbm"),
    /**
     * Disables the comparison between baseBranch and referenceBranch.
     */
    disableBranchComparison("false", "dbc", true),
    /**
     * The branch to compare baseBranch to.
     */
    referenceBranch("refs/remotes/origin/develop", "rb"),
    /**
     * Fetches the referenceBranch from the remote repository.
     */
    fetchReferenceBranch("false", "frb", true),
    /**
     * The branch that is compared to referenceBranch.
     */
    baseBranch("HEAD", "bb"),
    /**
     * Fetches the baseBranch from the remote repository
     */
    fetchBaseBranch("false", "fbb", true),
    /**
     * Controls whether or not to the merge-base mechanism to compare the branches.
     */
    compareToMergeBase("true", "ctmb", true),
    /**
     * Detects changed files that have not yet been committed.
     */
    uncommitted("true", "uc", true),
    /**
     * Detects files that are not yet tracked by git.
     */
    untracked("true", "ut", true),
    /**
     * Can be used to skip this extension on certain changes.
     */
    skipIfPathMatches("", "sipm"),
    /**
     * Can be used to exclude certain changed files from being detected as changed, reducing the number of modules to build.
     */
    excludePathsMatching("", "epm"),
    /**
     * Can be used to include only certain changed files from being detected as changed, reducing the number of modules to build.
     */
    includePathsMatching("", "ipm"),

    /**
     * Builds all modules, including upstream modules.
     */
    buildAll("false", "ba", true),
    /**
     * Can be used to active buildAll if no changes are detected (instead of just building the root module with goal validate).
     */
    buildAllIfNoChanges("false", "bainc", true),
    /**
     * Controls whether or not to build downstream modules.
     */
    buildDownstream("always", "bd", true),
    /**
     * Controls whether or not to build upstream modules.
     */
    buildUpstream("derived", "bu", true),
    /**
     * This property controls which upstream modules to build.
     */
    buildUpstreamMode("changed", "bum"),
    /**
     * This property disables the compilation/execution of tests for upstream modules.
     */
    skipTestsForUpstreamModules("false", "stfum", true),
    /**
     * This property allows adding arbitrary arguments/properties for upstream modules to further reduce overhead.
     */
    argsForUpstreamModules("", "afum"),
    /**
     * This property allows adding arbitrary arguments/properties for downstream modules to further reduce overhead.
     */
    argsForDownstreamModules("", "afdm"),
    /**
     * Defines artifact ids of modules to build forcibly.
     */
    forceBuildModules("", "fbm"),
    /**
     * Defines the packaging (e.g. jar) of modules that depend on changed modules but shall not be built.
     */
    excludeDownstreamModulesPackagedAs("", "edmpa"),
    /**
     * Disables special handling of explicitly selected projects (-pl, -f etc.).
     */
    disableSelectedProjectsHandling("false", "dsph", true),

    /**
     * Controls whether or not to fail on missing .git directory.
     */
    failOnMissingGitDir("true", "fomgd", true),
    /**
     * Controls whether or not to fail on any error.
     */
    failOnError("true", "foe", true),
    /**
     * Defines an optional logfile which GIB shall write all "impacted" modules to.
     */
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
        this.nameCandidatesForPluginProperties = Stream.of(name(), deprecatedName().orElse(null))
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
    public Optional<String> deprecatedName() {
        // might be overridden by specific enum instances
        return Optional.empty();
    }

    /**
     * @return the deprecated prefixed name or {@code null} if there is no such deprecated name for this property
     */
    public final String deprecatedPrefixedName() {
        return deprecatedName().map(PREFIX::concat).orElse(null);
    }

    public ValueWithOriginContext getValueWithOriginContext(Properties pluginProperties, Properties projectProperties) {
        Optional<ValueWithOriginContext> valueWithName = getValueWithOriginContext(nameCandidatesForPluginProperties, pluginProperties, "plugin");
        if (!valueWithName.isPresent()) {
            valueWithName = getValueWithOriginContext(nameCandidatesForSystemProperties, System.getProperties(), "system");
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

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isBoolean() {
        return defaultValue.equals("true") || defaultValue.equals("false");
    }

    protected Optional<Property> getPropertyToMigrateTo() {
        return Optional.empty();
    }

    private Optional<ValueWithOriginContext> getValueWithOriginContext(List<String> nameCandidates, Properties properties, String propertiesDesc) {
        return properties.isEmpty() ? Optional.empty() : nameCandidates.stream()
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
        String deprecatedName = prefixed ? deprecatedPrefixedName() : deprecatedName().orElse(null);
        if (name.equals(deprecatedName)) {
            LOGGER.warn("'{}' has been renamed to '{}' and the old name will be removed in an upcoming release. Please adjust your configuration!",
                    deprecatedName, prefixed ? prefixedName : name());
        }
        getPropertyToMigrateTo().ifPresent(prop -> LOGGER.warn("'{}' is deprecated and will be removed in an upcoming release. Please migrate to '{}'!", name,
                prefixed
                        ? prop.prefixedName + (name.equals(prefixedShortName) ? " (" + prop.prefixedShortName + ")" : "")
                        : prop.name()));
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
            throw new IllegalArgumentException(String.format(
                    "Invalid GIB properties found:%s%n%nAllowed properties:%n%s%nFor a plugin-<configuration>, use the properties without the %s prefix.",
                    errorDetails,
                    Property.exemplifyAll(),
                    PREFIX));
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

    static class ValueWithOriginContext {
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

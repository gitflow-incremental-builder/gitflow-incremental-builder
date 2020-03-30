package com.vackosar.gitflowincrementalbuild.control;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum Property {
    help("false", "h", true),
    enabled("true", "e", true),

    disableBranchComparison("false", "dbc", true),
    referenceBranch("refs/remotes/origin/develop", "rb"),
    fetchReferenceBranch("false", "frb", true),
    baseBranch("HEAD", "bb"),
    fetchBaseBranch("false", "fbb", true),
    useJschAgentProxy("true", "ujap"),
    compareToMergeBase("true", "ctmb", true),
    uncommited("true", "uc", true),
    untracked("true", "ut", true),
    excludePathRegex(Constants.NEVER_MATCH_REGEX, "epr"),

    buildAll("false", "ba", true),
    buildAllIfNoChanges("false", "bainc", true),
    buildDownstream("always", "bd", true),
    buildUpstream("derived", "bu", true),
    buildUpstreamMode("changed", "bum"),
    skipTestsForUpstreamModules("false", "stfum", true) {
        @Override
        public String deprecatedFullName() {
            return PREFIX + "skipTestsForNotImpactedModules";
        }
    },
    argsForUpstreamModules("", "afum") {
        @Override
        public String deprecatedFullName() {
            return PREFIX + "argsForNotImpactedModules";
        }
    },
    forceBuildModules("", "fbm"),
    excludeDownstreamModulesPackagedAs("", "edmpa"){
        @Override
        public String deprecatedFullName() {
            return PREFIX + "excludeTransitiveModulesPackagedAs";
        }
    },

    failOnMissingGitDir("true", "fomgd", true),
    failOnError("true", "foe", true);

    public static final String PREFIX = "gib.";

    private static final Logger LOGGER = LoggerFactory.getLogger(Property.class);

    private final String fullName;
    private final String shortName;
    private final String defaultValue;
    private final List<String> allNames;

    private final boolean mapEmptyValueToTrue;

    Property(String defaultValue, String unprefixedShortName) {
        this(defaultValue, unprefixedShortName, false);
    }

    Property(String defaultValue, String unprefixedShortName, boolean mapNoValueToTrue) {
        this.fullName = PREFIX + name();
        this.defaultValue = defaultValue;
        this.shortName = PREFIX + unprefixedShortName;
        this.allNames = Stream.of(fullName, shortName, deprecatedFullName())
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        this.mapEmptyValueToTrue = mapNoValueToTrue;
    }

    private String exemplify() {
        return String.format("%-85s", "<" + fullName + ">" + ( defaultValue == null ? "" : defaultValue ) + "</" + fullName + ">")
                + " <!-- or <" + shortName + ">... -->";
    }

    public String fullName() {
        return fullName;
    }

    public String shortName() {
        return shortName;
    }

    // only for descriptive output
    public String fullOrShortName() {
        return fullName + " (or " + shortName + ")";
    }

    public String deprecatedFullName() {
        // might be overridden by specific enum instances
        return null;
    }

    public List<String> allNames() {
        return allNames;
    }

    public String getValue(Properties projectProperties) {
        String value = Stream.of(System.getProperties(), projectProperties)
                .flatMap(props -> allNames.stream()
                        .map(name -> getValue(name, props)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(defaultValue);

        LOGGER.debug("{}={}", fullName, value);
        return value;
    }

    private String getValue(String name, Properties properties) {
        String value = properties.getProperty(name);
        if (value != null) {
            if (name.equals(deprecatedFullName())) {
                LOGGER.warn("{} has been replaced with {} and will be removed in an upcoming release. Please adjust your configuration!",
                        deprecatedFullName(), fullOrShortName());
            }
            if (mapEmptyValueToTrue && value.isEmpty()) {
                value = "true";
            }
        }
        return value;
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

    private static class Constants {
        private static final String NEVER_MATCH_REGEX = "(?!x)x";
    }
}

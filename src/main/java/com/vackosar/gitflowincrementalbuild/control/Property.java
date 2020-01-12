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
    enabled("true", "e"),

    disableBranchComparison("false", "dbc"),
    referenceBranch("refs/remotes/origin/develop", "rb"),
    fetchReferenceBranch("false", "frb"),
    baseBranch("HEAD", "bb"),
    fetchBaseBranch("false", "fbb"),
    compareToMergeBase("true", "ctmb"),
    uncommited("true", "uc"),
    untracked("true", "ut"),
    excludePathRegex(Constants.NEVER_MATCH_REGEX, "epr"),

    buildAll("false", "ba"),
    buildDownstream("always", "bd"),
    buildUpstream("derived", "bu"),
    buildUpstreamMode("changed", "bum"),
    skipTestsForUpstreamModules("false", "stfum") {
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
    excludeTransitiveModulesPackagedAs("", "etmpa"),

    failOnMissingGitDir("true", "fomgd"),
    failOnError("true", "foe");

    public static final String PREFIX = "gib.";

    private static final Logger LOGGER = LoggerFactory.getLogger(Property.class);

    private final String fullName;
    private final String shortName;
    private final String defaultValue;
    private final List<String> allNames;

    Property(String defaultValue, String unprefixedShortName) {
        this.fullName = PREFIX + name();
        this.defaultValue = defaultValue;
        this.shortName = PREFIX + unprefixedShortName;
        this.allNames = Stream.of(fullName, shortName, deprecatedFullName())
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
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
        if (value != null && name.equals(deprecatedFullName())) {
            LOGGER.warn("{} has been replaced with {} and will be removed in an upcoming release. Please adjust your configuration!",
                    deprecatedFullName(), fullOrShortName());
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

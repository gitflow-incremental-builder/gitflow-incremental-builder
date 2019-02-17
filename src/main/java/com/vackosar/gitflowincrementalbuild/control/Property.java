package com.vackosar.gitflowincrementalbuild.control;

import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum Property {
    enabled("true"),


    disableBranchComparison("false"),
    referenceBranch("refs/remotes/origin/develop"),
    fetchReferenceBranch("false"),
    baseBranch("HEAD"),
    fetchBaseBranch("false"),
    compareToMergeBase("true"),
    uncommited("true"),
    untracked("true"),
    excludePathRegex(Constants.NEVER_MATCH_REGEX),

    buildAll("false"),
    buildDownstream("always"),
    buildUpstream("derived"),
    buildUpstreamMode("changed"),
    skipTestsForUpstreamModules("false", "skipTestsForNotImpactedModules"),
    argsForUpstreamModules("", "argsForNotImpactedModules"),
    forceBuildModules(""),
    excludeTransitiveModulesPackagedAs(""),

    failOnMissingGitDir("true"),
    failOnError("true");

    public static final String PREFIX = "gib.";

    private static final Logger LOGGER = LoggerFactory.getLogger(Property.class);

    private final String fullName;
    private final String defaultValue;
    private final String deprecatedFullName;

    Property(String defaultValue) {
        this(defaultValue, null);
    }

    Property(String defaultValue, String deprecatedFullName) {
        this.fullName = PREFIX + name();
        this.defaultValue = defaultValue;
        this.deprecatedFullName = deprecatedFullName != null ? PREFIX + deprecatedFullName : null;
    }

    private String exemplify() {
        return "<" + fullName() + ">" + ( defaultValue == null ? "" : defaultValue )+ "</" + fullName() + ">";
    }

    public String fullName() {
        return fullName;
    }

    public String deprecatedFullName() {
        return deprecatedFullName;
    }

    public String getValue(Properties projectProperties) {
        String value = getValue(fullName, projectProperties);
        if (value != null) {
            return value;
        }
        if (deprecatedFullName != null) {
            value = getValue(deprecatedFullName, projectProperties);
            if (value != null) {
                LOGGER.warn("{} has been replaced with {} and will be removed in an upcoming release. Please adjust your configuration!",
                        deprecatedFullName, fullName);
                return value;
            }
        }
        return defaultValue;
    }

    private static String getValue(String fullName, Properties projectProperties) {
        return Optional.ofNullable(System.getProperty(fullName)).orElseGet(() -> projectProperties.getProperty(fullName));
    }

    public static String exemplifyAll() {
        StringBuilder builder = new StringBuilder();
        builder.append("<properties>\n");
        for (Property value :Property.values()) {
            builder.append("\t").append(value.exemplify()).append("\n");
        }
        builder.append("</properties>\n");
        return builder.toString();
    }

    private static class Constants {
        private static final String NEVER_MATCH_REGEX = "(?!x)x";
    }
}

package com.vackosar.gitflowincrementalbuild.control;

import java.util.Optional;
import java.util.Properties;

public enum Property {
    enabled("true"),
    repositorySshKey(""),
    disableBranchComparison("false"),
    referenceBranch("refs/remotes/origin/develop"),
    baseBranch("HEAD"),
    uncommited("true"),
    untracked("true"),
    skipTestsForNotImpactedModules("false"),
    argsForNotImpactedModules(""),
    buildAll("false"),
    forceBuildModules(""),
    excludeTransitiveModulesPackagedAs(""),
    compareToMergeBase("true"),
    fetchBaseBranch("false"),
    fetchReferenceBranch("false"),
    excludePathRegex(Constants.NEVER_MATCH_REGEX),
    failOnMissingGitDir("true"),
    failOnError("true");

    public static final String PREFIX = "gib.";

    private final String fullName;
    private final String defaultValue;

    Property(String defaultValue) {
        this.fullName = PREFIX + name();
        this.defaultValue = defaultValue;
    }

    private String exemplify() {
        return "<" + fullName() + ">" + ( defaultValue == null ? "" : defaultValue )+ "</" + fullName() + ">";
    }

    public String fullName() {
        return fullName;
    }

    public String getValue(Properties projectProperties) {
        return Optional.ofNullable(System.getProperty(fullName))
                .orElseGet(() -> projectProperties.getProperty(fullName, defaultValue));
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

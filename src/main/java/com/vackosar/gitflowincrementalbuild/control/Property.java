package com.vackosar.gitflowincrementalbuild.control;

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
    compareToMergeBase("true"),
    fetchBaseBranch("false"),
    fetchReferenceBranch("false"),
    excludePathRegex(Constants.NEVER_MATCH_REGEX),
    failOnMissingGitDir("true"),
    failOnError("true")
    ;

    public static final String PREFIX = "gib.";

    public final String defaultValue;

    Property(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    private String exemplify() {
        return "<" + fullName() + ">" + ( defaultValue == null ? "" : defaultValue )+ "</" + fullName() + ">";
    }

    public String fullName() {
        return PREFIX + this.name();
    }

    public String getValue() {
        return System.getProperty(fullName(), defaultValue);
    }

    public void setValue(String value) {
        if (value ==null) {
            System.clearProperty(fullName());
        } else {
            System.setProperty(fullName(), value);
        }
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

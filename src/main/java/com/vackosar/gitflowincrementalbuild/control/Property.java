package com.vackosar.gitflowincrementalbuild.control;

public enum Property {
    enabled("true"),
    repositorySshKey(null),
    referenceBranch("refs/remotes/origin/develop"),
    baseBranch("HEAD"),
    uncommited("true"),
    skipTestsForNotImpactedModules("false"),
    buildAll("false")
    ;

    public static final String PREFIX = "gib.";

    public final String defaultValue;

    Property(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    private String exemplify() {
        return "<" + fullName() + ">" + ( defaultValue == null ? "" : defaultValue )+ "</" + fullName() + ">";
    }

    private String fullName() {
        return PREFIX + this.name();
    }

    public String getValue() {
        return System.getProperty(fullName(), defaultValue);
    }

    public void setValue(String value) {
        System.setProperty(fullName(), value);
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
}

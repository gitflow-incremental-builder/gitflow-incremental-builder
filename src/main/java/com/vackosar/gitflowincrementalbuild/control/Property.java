package com.vackosar.gitflowincrementalbuild.control;

public enum Property {
    enabled("enable", "true"),
    key("key", null),
    referenceBranch("referenceBranch", "refs/remotes/origin/develop"),
    baseBranch("baseBranch", "HEAD"),
    uncommited("uncommited", "true");

    public static final String PREFIX = "gib.";

    public final String name;
    public final String defaultValue;

    Property(String name, String defaultValue) {
        this.name = PREFIX + name;
        this.defaultValue = defaultValue;
    }

    private String describe() {
        return name + "  defaults to " + defaultValue;
    }

    public String getValue() {
        return System.getProperty(name, defaultValue);
    }

    public void setValue(String value) {
        System.setProperty(name, value);
    }

    public static String describeAll() {
        StringBuilder builder = new StringBuilder();
        for (Property value :Property.values()) {
            builder.append(value.describe()).append("\n");
        }
        return builder.toString();
    }
}

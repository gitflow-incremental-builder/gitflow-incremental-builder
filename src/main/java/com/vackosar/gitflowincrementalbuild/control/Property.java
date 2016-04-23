package com.vackosar.gitflowincrementalbuild.control;

public class Property {

    public static final String PREFIX = "gib.";

    public final String key;
    public final String defaultValue;

    public Property(String key, String defaultValue) {
        this.key = PREFIX + key;
        this.defaultValue = defaultValue;
    }

    private String describe() {
        return key + "  defaults to " + defaultValue;
    }

    public String getValue() {
        return System.getProperty(key, defaultValue);
    }

    public void setValue(String value) {
        System.setProperty(key, value);
    }
}

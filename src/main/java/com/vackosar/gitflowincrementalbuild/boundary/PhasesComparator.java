package com.vackosar.gitflowincrementalbuild.boundary;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Based on https://maven.apache.org/ref/3.3.9/maven-core/lifecycles.html
 */
class PhasesComparator implements Comparator<String> {

    private static final List<String> list = Arrays.asList(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test",
            "prepare-package",
            "package",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install");

    public @Override int compare(String o1, String o2) {
        return list.indexOf(o1) - list.indexOf(o2);
    }

}

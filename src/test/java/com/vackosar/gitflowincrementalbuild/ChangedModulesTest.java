package com.vackosar.gitflowincrementalbuild;

import com.google.inject.Guice;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChangedModulesTest extends RepoTest {

    private static final String USER_DIR = "user.dir";

    @Test
    public void list() throws Exception {
        final Path pom = Paths.get(System.getProperty(USER_DIR)).resolve("parent/pom.xml");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4")
        ));
        final Set<Path> actual = Guice.createInjector(new Module(new String[]{pom.toString()})).getInstance(ChangedModules.class).list(pom);
        Assert.assertEquals(expected, actual);
    }
}

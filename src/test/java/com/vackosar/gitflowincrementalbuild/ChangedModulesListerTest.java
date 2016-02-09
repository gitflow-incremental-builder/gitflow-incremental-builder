package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChangedModulesListerTest extends RepoTest {
    @Test
    public void list() throws Exception {
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get("child2/subchild2"),
                Paths.get("child3"),
                Paths.get("child4")
        ));
        Assert.assertEquals(expected, new ChangedModulesLister().act(pom));
    }
}

package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DiffListerTest extends RepoTest {

    @Test
    public void list() throws Exception {
        String workDir = RepoMock.TEST_WORK_DIR + "tmp/repo/";
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file22"),
                Paths.get(workDir + "/parent/child3/src/resources/file1"),
                Paths.get(workDir + "/parent/child4/pom.xml")
                ));
        Assert.assertEquals(expected, new DiffLister().act());
    }

    @Test
    public void listInSubdir() throws Exception {
        Path workDir = Paths.get(RepoMock.TEST_WORK_DIR).resolve("tmp/repo/parent/child2");
        final Set<Path> expected = new HashSet<>(Arrays.asList(
                workDir.resolve("subchild2/src/resources/file2"),
                workDir.resolve("subchild2/src/resources/file22"),
                workDir.resolve("../child3/src/resources/file1").normalize(),
                workDir.resolve("../child4/pom.xml").normalize()
        ));
        Assert.assertEquals(expected, new DiffLister().act());
    }

}

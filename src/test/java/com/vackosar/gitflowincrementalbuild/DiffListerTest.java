package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DiffListerTest extends RepoTest {

    @Test
    public void list() throws Exception {
        String workDir = RepoMock.TEST_WORK_DIR + "tmp/repo/";
        final Path[] expected = {
                Paths.get(workDir + "/parent/child2/subchild2/src/resources/file2"),
                Paths.get(workDir + "/parent/child3/src/resources/file1")
        };
        Assert.assertArrayEquals(expected, new DiffLister().act().toArray());
    }

}

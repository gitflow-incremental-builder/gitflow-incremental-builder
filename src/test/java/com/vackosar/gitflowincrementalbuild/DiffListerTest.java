package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DiffListerTest {

    public static final String TEST_WORK_DIR = System.getProperty("user.dir") + "/";

    @Test
    public void init() throws Exception {
        final RepoMock repoMock = new RepoMock();
        String workDir = RepoMock.TEST_WORK_DIR + "tmp/repo/";
        final Path[] expected = {Paths.get(workDir + "/parent/child1/src/resources/file1")};
        Assert.assertArrayEquals(expected, new DiffLister().act().toArray());
        repoMock.close();
    }

}

package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ChangedModulesListerTest {
    @Test
    public void list() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        String workDir = DiffListerTest.TEST_WORK_DIR + "tmp/repo/";
        System.setProperty("user.dir", workDir);
        Path[] expected = new Path[] {Paths.get(RepoMock.WORK_DIR + "parent/child1")};
        Assert.assertArrayEquals(expected, new ChangedModulesLister().act(pom).toArray());
        repoMock.close();
    }
}

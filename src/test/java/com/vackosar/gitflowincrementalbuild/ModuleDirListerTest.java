package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ModuleDirListerTest {

    @Test
    public void listPoms() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        Path[] expected = new Path[] {
                Paths.get(RepoMock.WORK_DIR + "parent"),
                Paths.get(RepoMock.WORK_DIR + "parent/child1"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2/subchild1"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2/subchild2"),
                Paths.get(RepoMock.WORK_DIR + "parent/child3"),
        };
        Assert.assertArrayEquals(expected, new ModuleDirLister().act(pom).toArray());
        repoMock.close();
    }

}

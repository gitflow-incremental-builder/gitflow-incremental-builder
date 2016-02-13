package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ModuleDirsTest extends RepoTest {

    @Test
    public void listPoms() throws Exception {
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        Path[] expected = new Path[] {
                Paths.get(RepoMock.WORK_DIR + "parent"),
                Paths.get(RepoMock.WORK_DIR + "parent/child1"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2/subchild1"),
                Paths.get(RepoMock.WORK_DIR + "parent/child2/subchild2"),
                Paths.get(RepoMock.WORK_DIR + "parent/child3"),
                Paths.get(RepoMock.WORK_DIR + "parent/child4"),
                Paths.get(RepoMock.WORK_DIR + "parent/child4/subchild41"),
                Paths.get(RepoMock.WORK_DIR + "parent/child4/subchild42"),
        };
        Assert.assertArrayEquals(expected, new ModuleDirs().list(pom).toArray());
    }

}

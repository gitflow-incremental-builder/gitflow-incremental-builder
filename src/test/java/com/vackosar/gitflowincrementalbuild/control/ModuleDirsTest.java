package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.mocks.LocalRepoMock;
import com.vackosar.gitflowincrementalbuild.mocks.RepoTest;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ModuleDirsTest extends RepoTest {

    @Test
    public void listPoms() throws Exception {
        final Path pom = Paths.get(LocalRepoMock.WORK_DIR + "parent/pom.xml");
        final List<Path> expected = Arrays.asList(
                Paths.get(LocalRepoMock.WORK_DIR + "parent"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child1"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child2"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child2/subchild1"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child2/subchild2"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child3"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child4"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child4/subchild41"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child4/subchild42"),
                Paths.get(LocalRepoMock.WORK_DIR + "parent/child5")
        );
        Assert.assertEquals(expected, new ModuleDirs().list(pom));
    }

}

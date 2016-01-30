package com.vackosar.gitflowincrementalbuild;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ChangedModulesListerTest extends RepoTest {
    @Test
    public void list() throws Exception {
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        Path[] expected = new Path[] {Paths.get("child2/subchild2"), Paths.get("child3")};
        Assert.assertArrayEquals(expected, new ChangedModulesLister().act(pom).toArray());
    }
}

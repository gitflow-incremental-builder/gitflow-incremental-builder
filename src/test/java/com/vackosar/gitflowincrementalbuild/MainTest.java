package com.vackosar.gitflowincrementalbuild;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MainTest {
    @Test
    public void list() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Path pom = Paths.get(RepoMock.WORK_DIR + "parent/pom.xml");
        String workDir = DiffListerTest.TEST_WORK_DIR + "tmp/repo/";
        System.setProperty("user.dir", workDir);
        Path[] expected = new Path[] {Paths.get("child1")};
        Main.main(new String[]{pom.toString()});
//        Assert.assertArrayEquals("child1", );
        repoMock.close();
    }
}

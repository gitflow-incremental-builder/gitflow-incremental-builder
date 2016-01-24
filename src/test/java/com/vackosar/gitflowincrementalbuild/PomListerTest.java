package com.vackosar.gitflowincrementalbuild;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class PomListerTest {

    @Test
    public void listPoms() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Path pom = Paths.get(repoMock.WORK_DIR + "parent/pom.xml");
        System.out.println(Arrays.deepToString(new PomLister().act(pom).toArray()));
        repoMock.close();
    }

}

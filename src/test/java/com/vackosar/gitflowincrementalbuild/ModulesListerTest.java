package com.vackosar.gitflowincrementalbuild;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class ModulesListerTest {
    private static final String TEST_WORK_DIR = System.getProperty("user.dir") + "/";

    @Test
    public void listModules() throws Exception {
        RepoMock repoMock = new RepoMock();
        final Path pom = Paths.get(repoMock.WORK_DIR + "pom.xml");
        final List<String> modules = Files.readAllLines(pom)
                .stream()
                .filter(s -> s.contains("<module>"))
                .map(s1 -> s1.replaceFirst("<module>([^<]*)</module>", "$1"))
                .collect(Collectors.toList());
        repoMock.close();
    }
}

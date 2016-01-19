package com.vackosar.gitflowincrementalbuilder.utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MavenProjectGenerator {

    @Test
    public void generateTest() throws IOException {
        new MavenProjectGenerator().generate(".");
    }

    public void generate(String path) throws IOException {
        final File root = new File(path);
        Assert.assertTrue(root.isDirectory());
        final String resource = getClass().getClassLoader().getResource("pom.xml.template").getPath();
        final List<String> pomTemplate = Files.readAllLines(Paths.get(resource.replaceAll("^/([a-zA-Z]:)", "$1")));
        Files.write(Paths.get(path + "pom.xml"), pomTemplate);
    }
}

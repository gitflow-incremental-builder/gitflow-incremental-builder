package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws GitAPIException, IOException {
        if (args.length != 1) {
            System.out.println("Usage: [path to pom]\nWorkdirectory is expected to be in git root.");
        }
        final Path pom = Paths.get(args[0]);
        final String modules = new ChangedModulesLister()
                .act(pom)
                .stream()
                .map(s -> "," + s)
                .collect(Collectors.joining())
                .replaceFirst(",", "");
        System.out.println(modules);
    }
}

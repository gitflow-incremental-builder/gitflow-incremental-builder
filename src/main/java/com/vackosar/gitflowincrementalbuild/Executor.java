package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class Executor {

    @Inject private ChangedModules changedModules;
    @Inject private Arguments arguments;

    public void act() throws GitAPIException, IOException {
        final String modules = changedModules
                .list(arguments.pom)
                .stream()
                .sorted()
                .map(commaPrefix())
                .collect(Collectors.joining())
                .replaceFirst(",", "");
        System.out.println(modules);
    }

    private static Function<Path, String> commaPrefix() {
        return s -> "," + s;
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.ChangedModules;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
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

    public Set<String> getArtifactIds() throws GitAPIException, IOException {
        return changedModules
                .list(arguments.pom)
                .stream()
                .sorted()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toSet());
    }

    private static Function<Path, String> commaPrefix() {
        return s -> "," + s;
    }
}

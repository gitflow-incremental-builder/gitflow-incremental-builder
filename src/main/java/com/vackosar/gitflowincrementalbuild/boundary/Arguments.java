package com.vackosar.gitflowincrementalbuild.boundary;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

@Singleton
public class Arguments {

    public final Path pom;
    public final Optional<Path> key;

    @Inject
    public Arguments(String[] args, Path workDir) throws IOException {
        if (! Arrays.asList(1,2).contains(args.length)) {
            System.out.println("Usage: [path to pom] [unecrypted key path]\nWorkdirectory is expected to be in git root.");
        }
        pom = workDir.resolve(args[0]).toAbsolutePath().toRealPath().normalize();
        if (args.length == 2) {
            key = Optional.of(workDir.resolve(args[0]).toAbsolutePath().toRealPath().normalize());
        } else {
            key = Optional.empty();
        }
    }
}

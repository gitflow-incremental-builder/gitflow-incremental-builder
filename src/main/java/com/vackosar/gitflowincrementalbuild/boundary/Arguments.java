package com.vackosar.gitflowincrementalbuild.boundary;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

@Singleton
public class Arguments {

    public final Path pom;

    @Inject
    public Arguments(String[] args, Path workDir) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: [path to pom]\nWorkdirectory is expected to be in git root.");
        }
        pom = workDir.resolve(args[0]).toAbsolutePath().toRealPath().normalize();
    }
}

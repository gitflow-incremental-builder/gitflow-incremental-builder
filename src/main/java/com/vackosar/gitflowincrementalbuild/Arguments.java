package com.vackosar.gitflowincrementalbuild;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;

@Singleton
public class Arguments {

    public final Path pom;

    @Inject
    public Arguments(String[] args, Path workDir) {
        if (args.length != 1) {
            System.out.println("Usage: [path to pom]\nWorkdirectory is expected to be in git root.");
        }
        pom = workDir.resolve(args[0]);
    }
}

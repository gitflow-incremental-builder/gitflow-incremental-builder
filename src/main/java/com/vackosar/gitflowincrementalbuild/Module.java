package com.vackosar.gitflowincrementalbuild;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Module extends AbstractModule {

    private final String[] args;

    public Module(String[] args) {
        this.args = args;
    }

    @Provides
    @Singleton
    public Git provideGit(Path workDir) throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        final FileRepositoryBuilder gitDir = builder.findGitDir(workDir.toFile());
        if (gitDir == null) {
            throw new IllegalArgumentException("Git repository root directory not found ascending from current working directory:'" + workDir + "'.");
        }
        return Git.wrap(builder.build());
    }

    @Provides
    @Singleton
    public Arguments provideArguments(Path workDir) throws IOException {
        return new Arguments(args, workDir);
    }

    @Provides
    @Singleton
    public Path provideWorkDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    @Override
    protected void configure() {}
}

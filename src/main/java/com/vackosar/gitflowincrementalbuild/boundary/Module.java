package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Module extends AbstractModule {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String[] args;

    public Module(String[] args) {
        this.args = args;
    }

    @Provides
    @Singleton
    public Git provideGit(Path workDir, Arguments arguments) throws IOException, GitAPIException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        final FileRepositoryBuilder gitDir = builder.findGitDir(workDir.toFile());
        if (gitDir == null) {
            throw new IllegalArgumentException("Git repository root directory not found ascending from current working directory:'" + workDir + "'.");
        }
        final Git git = Git.wrap(builder.build());
        return git;
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

package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.impl.StaticLoggerBinder;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

public class GuiceModule extends AbstractModule {

    private final Logger logger;
    private final MavenSession mavenSession;

    public GuiceModule(Logger logger, MavenSession mavenSession) {
        this.logger = logger;
        this.mavenSession = mavenSession;
    }

    @Provides
    @Singleton
    public Git provideGit(Path workDir, StaticLoggerBinder staticLoggerBinder) throws IOException, GitAPIException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(workDir.toFile());
        if (builder.getGitDir() == null) {
            throw new IllegalArgumentException("Git repository root directory not found ascending from current working directory:'" + workDir + "'.");
        }
        logger.info("Git root is: " + String.valueOf(builder.getGitDir().getAbsolutePath()));
        return Git.wrap(builder.build());
    }

    @Provides @Singleton public MavenSession provideMavenSession() { return mavenSession; }

    @Provides @Singleton public Logger provideLogger() { return logger; }

    @Provides @Singleton public Path provideWorkDir() {
        return mavenSession.getCurrentProject().getBasedir().toPath();
    }

    @Override
    protected void configure() {}

}

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
import java.io.File;
import java.io.IOException;

public class GuiceModule extends AbstractModule {

    private final Logger logger;
    private final MavenSession mavenSession;

    public GuiceModule(Logger logger, MavenSession mavenSession) {
        this.logger = logger;
        this.mavenSession = mavenSession;
    }

    @Provides
    @Singleton
    public Git provideGit(final StaticLoggerBinder staticLoggerBinder) throws IOException, GitAPIException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = mavenSession.getCurrentProject().getBasedir().toPath().toFile();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            throw new IllegalArgumentException("Git repository root directory not found ascending from current working directory:'" + pomDir + "'.");
        }
        logger.info("Git root is: " + String.valueOf(builder.getGitDir().getAbsolutePath()));
        return Git.wrap(builder.build());
    }

    @Provides @Singleton public MavenSession provideMavenSession() { return mavenSession; }

    @Provides @Singleton public Logger provideLogger() { return logger; }

    @Override
    protected void configure() {}

}

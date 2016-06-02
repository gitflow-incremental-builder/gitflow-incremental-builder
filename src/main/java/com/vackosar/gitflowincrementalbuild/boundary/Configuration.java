package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Singleton
public class Configuration {

    private static final String MAKE_UPSTREAM = "make-upstream";

    public final boolean enabled;
    public final Optional<Path> key;
    public final String referenceBranch;
    public final String baseBranch;
    public final boolean uncommited;
    public final boolean makeUpstream;
    public final boolean skipTestsForNotImpactedModules;
    public final boolean buildAll;

    @Inject
    public Configuration(Path workDir, MavenSession session) throws IOException {
        try {
            mergeCurrentProjectProperties(session);
            checkProperties();
            enabled = Boolean.valueOf(Property.enabled.getValue());
            key = parseKey(workDir);
            referenceBranch = Property.referenceBranch.getValue();
            baseBranch = Property.baseBranch.getValue();
            uncommited = Boolean.valueOf(Property.uncommited.getValue());
            makeUpstream = MAKE_UPSTREAM.equals(session.getRequest().getMakeBehavior());
            skipTestsForNotImpactedModules = Boolean.valueOf(Property.skipTestsForNotImpactedModules.getValue());
            buildAll = Boolean.valueOf(Property.buildAll.getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> parseKey(Path workDir) throws IOException {
        String keyOptionValue = Property.repositorySshKey.getValue();
        if (keyOptionValue != null) {
            return Optional.of(workDir.resolve(keyOptionValue).toAbsolutePath().toRealPath().normalize());
        } else {
            return Optional.empty();
        }
    }

    private void mergeCurrentProjectProperties(MavenSession mavenSession) {
        mavenSession.getTopLevelProject().getProperties().entrySet().stream()
                .filter(e->e.getKey().toString().startsWith(Property.PREFIX))
                .filter(e->System.getProperty(e.getKey().toString()) == null)
                .forEach(e->System.setProperty(e.getKey().toString(), e.getValue().toString()));
    }

    private void checkProperties() throws MavenExecutionException {
        try {
            System.getProperties().entrySet().stream().map(Map.Entry::getKey)
                    .filter(o -> o instanceof String).map(o -> (String) o)
                    .filter(s -> s.startsWith(Property.PREFIX))
                    .map(s -> s.replaceFirst(Property.PREFIX, ""))
                    .forEach(Property::valueOf);
        } catch (IllegalArgumentException e) {
            throw new MavenExecutionException("Invalid invalid GIB property found. Allowed properties: \n" + Property.exemplifyAll(), e);
        }
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import com.vackosar.gitflowincrementalbuild.control.Property;
import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class Properties {

    public static final Property ENABLE_PROP = new Property("enable", Boolean.TRUE.toString());
    public static final Property KEY_PROP = new Property("key", null);
    public static final Property REF_BRANCH_PROP = new Property("reference.branch", "refs/remotes/origin/develop");
    public static final Property BASE_BRANCH_PROP = new Property("base.branch", "HEAD");
    public static final Property UNCOMMITED_PROP = new Property("uncommited", Boolean.TRUE.toString());

    public final boolean enabled;
    public final Optional<Path> key;
    public final String referenceBranch;
    public final String branch;
    public final boolean uncommited;

    @Inject
    public Properties(Path workDir, MavenSession session) throws IOException {
        try {
            mergeCurrentProjectProperties(session);
            enabled = Boolean.valueOf(ENABLE_PROP.getValue());
            key = parseKey(workDir);
            referenceBranch = REF_BRANCH_PROP.getValue();
            branch = BASE_BRANCH_PROP.getValue();
            uncommited = Boolean.valueOf(UNCOMMITED_PROP.getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> parseKey(Path workDir) throws IOException {
        String keyOptionValue = KEY_PROP.getValue();
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
}

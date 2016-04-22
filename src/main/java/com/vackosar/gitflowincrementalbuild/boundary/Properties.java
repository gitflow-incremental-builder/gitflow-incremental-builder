package com.vackosar.gitflowincrementalbuild.boundary;

import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class Properties {

    private static final String PREFIX = "gib.";
    private static final GibProperty ENABLE_PROP = new GibProperty("enable", Boolean.TRUE.toString());
    private static final GibProperty KEY_PROP = new GibProperty("key", null);
    private static final GibProperty REF_BRANCH_PROP = new GibProperty("reference.branch", "refs/remotes/origin/develop");
    private static final GibProperty BASE_BRANCH_PROP = new GibProperty("base.branch", "HEAD");
    private static final GibProperty UNCOMMITED_PROP = new GibProperty("uncommited", Boolean.TRUE.toString());

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

    private static class GibProperty {

        public final String key;
        public final String defaultValue;

        public GibProperty(String key, String defaultValue) {
            this.key = PREFIX + key;
            this.defaultValue = defaultValue;
        }

        private String describe() {
            return key + "  defaults to " + defaultValue;
        }

        public String getValue() {
            return System.getProperty(key, defaultValue);
        }
    }

    private void mergeCurrentProjectProperties(MavenSession mavenSession) {
        mavenSession.getTopLevelProject().getProperties().entrySet().stream()
                .filter(e->e.getKey().toString().startsWith(PREFIX))
                .filter(e->System.getProperty(e.getKey().toString()) == null)
                .forEach(e->System.setProperty(e.getKey().toString(), e.getValue().toString()));
    }
}

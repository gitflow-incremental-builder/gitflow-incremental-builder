package com.vackosar.gitflowincrementalbuild.boundary;

import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class GibProperties {

    private static final GibProperty KEY_PROP = new GibProperty("gib.key", null);
    private static final GibProperty REF_BRANCH_PROP = new GibProperty("gib.reference.branch", "refs/remotes/origin/develop");
    private static final GibProperty BASE_BRANCH_PROP = new GibProperty("gib.base.branch", "HEAD");

    public final Path pom;
    public final Optional<Path> key;
    public final String referenceBranch;
    public final String branch;

    @Inject
    public GibProperties(Path workDir, MavenSession session) throws IOException {
        try {
            pom = session.getCurrentProject().getFile().toPath();
            key = parseKey(workDir);
            referenceBranch = REF_BRANCH_PROP.getValue();
            branch = BASE_BRANCH_PROP.getValue();
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
            this.key = key;
            this.defaultValue = defaultValue;
        }

        private String describe() {
            return key + "  defaults to " + defaultValue;
        }

        public String getValue() {
            return System.getProperty(key, defaultValue);
        }
    }
}

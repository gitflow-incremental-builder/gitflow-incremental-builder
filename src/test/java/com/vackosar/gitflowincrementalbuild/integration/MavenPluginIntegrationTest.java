package com.vackosar.gitflowincrementalbuild.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}
 * registered directly as a Maven extension (not as a plugin).
 */
public class MavenPluginIntegrationTest extends MavenIntegrationTestBase {

    @Override
    @BeforeEach
    void initialInstall(TestInfo testInfo) throws IOException, InterruptedException {
        Path projectRoot = localRepoMock.getBaseCanonicalBaseFolder().toPath();
        Files.move(projectRoot.resolve("build-parent/pom-plugin.xml"), projectRoot.resolve("build-parent/pom.xml"), StandardCopyOption.REPLACE_EXISTING);
        super.initialInstall(testInfo);
    }
}

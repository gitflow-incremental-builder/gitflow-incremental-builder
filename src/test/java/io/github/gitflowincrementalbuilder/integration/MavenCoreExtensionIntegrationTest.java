package io.github.gitflowincrementalbuilder.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import io.github.gitflowincrementalbuilder.config.Configuration;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}
 * registered directly as a "core" Maven extension (not as a plugin) in {@code .mvn/extensions.xml}
 */
public class MavenCoreExtensionIntegrationTest extends MavenIntegrationTestBase {

    @Override
    @BeforeEach
    void initialInstall(TestInfo testInfo) throws Exception {
        Files.move(repoPath.resolve("build-parent/pom-none.xml"), repoPath.resolve("build-parent/pom.xml"), StandardCopyOption.REPLACE_EXISTING);

        writeExtensionsXml();

        super.initialInstall(testInfo);
    }

    private void writeExtensionsXml() throws IOException {
        String[] pluginGA = Configuration.PLUGIN_KEY.split(":");
        String extensionXml =
                "<extensions xmlns=\"http://maven.apache.org/EXTENSIONS/1.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "  xsi:schemaLocation=\"http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd\">\n" +
                "  <extension>\n" +
                "    <groupId>" + pluginGA[0] + "</groupId>\n" +
                "    <artifactId>" + pluginGA[1] + "</artifactId>\n" +
                "    <version>" + gibVersion + "</version>\n" +
                "  </extension>\n" +
                "</extensions>";
        Path extensionsPath = Files.createDirectory(repoPath.resolve(".mvn")).resolve("extensions.xml");
        Files.write(extensionsPath, extensionXml.getBytes(StandardCharsets.UTF_8));
    }
}

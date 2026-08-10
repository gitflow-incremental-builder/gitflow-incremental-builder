package io.github.gitflowincrementalbuilder.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}
 * registered directly as a "core" Maven extension (not as a plugin) in {@code .mvn/extensions.xml}
 */
public class MavenCoreExtensionIntegrationTest extends MavenIntegrationTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCoreExtensionIntegrationTest.class);

    private static boolean classLoadingStrategyElementSupported;

    @BeforeAll
    static void determineIfclassLoadingStrategyElementSupported() {
        // Note: This maven version check does not aim to cover all possible versions from 3.6.3 to 3.8.8 (exclusive),
        // instead it only checks for 3.6.3 and 3.8.7 (also fully aware that 3.8.7 is not covered in CI anymore).
        boolean maven387or363 = Optional.ofNullable(System.getProperty("maven.version"))
                .map(ver -> ver.split("\\."))
                .map(verParts -> "3".equals(verParts[0])
                        && ("6".equals(verParts[1]) || "8".equals(verParts[1]))
                        && ("3".equals(verParts[2]) || "7".equals(verParts[2])))
                .orElseGet(() -> {
                    LOGGER.warn("System property maven.version not set, assuming 3.8.8 or higher!");
                    return false;
                });
        classLoadingStrategyElementSupported = !maven387or363;
    }

    @Override
    @BeforeEach
    void initialInstall(TestInfo testInfo) throws Exception {
        Files.move(repoPath.resolve("build-parent/pom-none.xml"), repoPath.resolve("build-parent/pom.xml"), StandardCopyOption.REPLACE_EXISTING);

        writeExtensionsXml();

        super.initialInstall(testInfo);
    }

    @Test
    @Override
    public void testOnly_withDependent() throws Exception {
        super.testOnly_withDependent();

        if (classLoadingStrategyElementSupported) {
            assertThat(latestBuildOutput).doesNotContain("MNG-6965");
        } else {
            assertThat(latestBuildOutput).contains("MNG-6965");
        }
    }

    @Test
    public void afterOtherExtension() throws Exception {
        final String output = executeBuild("-am", "-D" + AddUpstreamSyntheticProjectParticipant.PROP_TARGET_ARTIFACT_ID + "=child3", "-Dgib.rpdgm=auto");

        assertThat(output).doesNotContain("Building child1")
                .doesNotContain("Building child2")
                .doesNotContain("Building subchild1")
                .doesNotContain("Building subchild42")
                .contains("Building subchild2")
                .contains("Building child3")
                .contains("Building child4")
                .contains("Building subchild41")
                .contains("Building child6")
                .contains("Building IT Synthetic Upstream Project 1.0.0-SNAPSHOT");
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
                (classLoadingStrategyElementSupported ? "    <classLoadingStrategy>plugin</classLoadingStrategy>\n" : "") +
                "  </extension>\n" +
                // for AddUpstreamSyntheticProjectParticipant, must be defined after GIB to be executed first (sic!)
                "  <extension>\n" +
                "    <groupId>" + pluginGA[0] + "</groupId>\n" +
                "    <artifactId>" + pluginGA[1] + "-it-support</artifactId>\n" +
                "    <version>" + gibVersion + "</version>\n" +
                "  </extension>\n" +
                "</extensions>";
        Path extensionsPath = Files.createDirectory(repoPath.resolve(".mvn")).resolve("extensions.xml");
        Files.write(extensionsPath, extensionXml.getBytes(StandardCharsets.UTF_8));
    }
}

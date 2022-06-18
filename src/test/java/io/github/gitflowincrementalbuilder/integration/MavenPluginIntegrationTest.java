package io.github.gitflowincrementalbuilder.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.mojo.MojoParametersGeneratingByteBuddyPlugin;

/**
 * Integration test running the {@code mvn} command on a test project with active {@code gitflow-incremental-builder}
 * registered as a Maven plugin (not directly as an extension).
 */
public class MavenPluginIntegrationTest extends MavenIntegrationTestBase {

    @Override
    @BeforeEach
    void initialInstall(TestInfo testInfo) throws Exception {
        Files.move(repoPath.resolve("build-parent/pom-plugin.xml"), repoPath.resolve("build-parent/pom.xml"), StandardCopyOption.REPLACE_EXISTING);
        super.initialInstall(testInfo);
    }

    @Test
    public void helpPlugin() throws IOException, InterruptedException {
        final String output = executeBuild(
                "help:describe",
                "-Dplugin=" + Configuration.PLUGIN_KEY + ":" + gibVersion,
                "-Ddetail", prop(Property.disable, "true"),
                "-N");
        assertThat(output).contains("This plugin has 1 goal:");
        assertThat(output).contains(MojoParametersGeneratingByteBuddyPlugin.FAKE_MOJO_NAME);
        assertThat(output).contains("Available parameters:");
        // just a sample
        assertThat(output).contains(Property.argsForUpstreamModules.name());
    }
}

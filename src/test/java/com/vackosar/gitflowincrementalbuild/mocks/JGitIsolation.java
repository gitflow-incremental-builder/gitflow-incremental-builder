package com.vackosar.gitflowincrementalbuild.mocks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.mockito.AdditionalAnswers;

/**
 * Ensures that JGit is isolated from system and user config to avoid inconsistent test results. As JGit does not provide a mechanism to skip user config,
 * this class replaces {@link SystemReader#getInstance() org.eclipse.jgit.util.SystemReader} with a mockito spy that returns an empty {@link FileBasedConfig}
 * when {@link SystemReader#openUserConfig(Config, FS)} is called. Although system config can be skipped via
 * {@link org.eclipse.jgit.lib.Constants#GIT_CONFIG_NOSYSTEM_KEY}, the mockito spy is also active for {@link SystemReader#openSystemConfig(Config, FS)} to
 * allow for a consistent test behaviour in IDEs and on the command line.
 *
 * @author famod
 */
public class JGitIsolation {

    static {
        // inspired by anonymous class in org.eclipse.jgit.util.SystemReader.Default.openSystemConfig(Config, FS)
        FileBasedConfig emptyConfig = new FileBasedConfig(null, FS.DETECTED) {
            @Override
            public void load() {
                // empty, do not load
            }

            @Override
            public boolean isOutdated() {
                // regular class would bomb here
                return false;
            }
        };

        // note: cannot directly use Mockito.spy(SystemReader.getInstance()) because org.eclipse.jgit.util.SystemReader.Default is invisible
        SystemReader spiedSystemReader = mock(SystemReader.class, withSettings()
                .defaultAnswer(AdditionalAnswers.delegatesTo(SystemReader.getInstance()))
                .lenient());
        doReturn(emptyConfig).when(spiedSystemReader).openSystemConfig(any(Config.class), any(FS.class));
        doReturn(emptyConfig).when(spiedSystemReader).openUserConfig(any(Config.class), any(FS.class));
        SystemReader.setInstance(spiedSystemReader);
    }

    public static void ensureIsolatedFromSystemAndUserConfig() {
        // noting to do, static block will kick in automatically and only once
    }
}

package com.vackosar.gitflowincrementalbuild.mocks;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.vackosar.gitflowincrementalbuild.control.DifferentFilesTest;

public class EmptyLocalRepoMock implements AutoCloseable {

    static {
        JGitIsolation.ensureIsolatedFromSystemAndUserConfig();
    }

    private final Path repoDir;
    private final Git git;

    public EmptyLocalRepoMock(Path allReposBaseDir) throws IOException, URISyntaxException, GitAPIException {
        this.repoDir = allReposBaseDir.toAbsolutePath().resolve("tmp/emptyrepo/");
        Files.createDirectories(repoDir);

        git = Git.init().setDirectory(repoDir.toFile()).call();
    }

    public Git getGit() {
        return git;
    }

    @Override
    public void close() {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
    }

    public Path getRepoDir() throws IOException {
        return repoDir;
    }

    public static void withBasicPom(Path allReposBaseDir, Callback callback) throws IOException, URISyntaxException, GitAPIException {
        try (InputStream basicPomData = DifferentFilesTest.class.getResourceAsStream("/pom-basic.xml");
                EmptyLocalRepoMock emptyLocalRepoMock = new EmptyLocalRepoMock(allReposBaseDir)) {
            Files.copy(basicPomData, emptyLocalRepoMock.repoDir.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);

            callback.invoke(emptyLocalRepoMock);
        }
    }

    public static interface Callback {
        void invoke(EmptyLocalRepoMock emptyLocalRepoMock) throws GitAPIException, IOException;
    }
}

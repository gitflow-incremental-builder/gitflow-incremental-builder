package com.vackosar.gitflowincrementalbuild.mocks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class LocalRepoMock implements AutoCloseable {

    public static final String TEST_WORK_DIR = System.getProperty("user.dir") + "/";
    public static final String WORK_DIR = TEST_WORK_DIR + "tmp/repo/";
    private static final File REPO = new File(TEST_WORK_DIR + "tmp/repo");
    private static final File ZIP = new File(TEST_WORK_DIR + "src/test/resources/template.zip");
    private RemoteRepoMock remoteRepo = new RemoteRepoMock(false);
    private Git git;

    public LocalRepoMock() throws IOException, URISyntaxException, GitAPIException {
        new UnZiper().act(ZIP, REPO);
        git = new Git(new FileRepository(new File(WORK_DIR + "/.git")));
        configureRemote(git, remoteRepo.repoUrl);
        git.fetch().call();
    }

    public Git getGit() {
        return git;
    }

    public void invalidateConfigration() throws IOException, URISyntaxException {
        configureRemote(git, "git://localhost:8888/repo.git");
    }

    private void configureRemote(Git git, String repoUrl) throws URISyntaxException, IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.clear();
        config.setString("remote", "origin" ,"fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("remote", "origin" ,"push", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("branch", "master", "remote", "origin");
        config.setString("branch", "master", "merge", "refs/heads/master");
        config.setString("push", null, "default", "current");
        RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
        URIish uri = new URIish(repoUrl);
        remoteConfig.addURI(uri);
        remoteConfig.addFetchRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.addPushRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.update(config);
        config.save();
    }


    public void close() throws Exception {
        remoteRepo.close();
        delete(REPO);
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new RuntimeException("Failed to delete file: " + f);
        }
    }
}

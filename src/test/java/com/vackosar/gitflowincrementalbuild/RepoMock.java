package com.vackosar.gitflowincrementalbuild;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class RepoMock implements AutoCloseable {

    private static final String TEST_WORK_DIR = System.getProperty("user.dir") + "/";
    private static final File REPO = new File(TEST_WORK_DIR + "tmp/repo");
    private static final File ZIP = new File(TEST_WORK_DIR + "src/test/resources/template.zip");
    private RemoteRepoMock remoteRepo = new RemoteRepoMock(false);
    private Git git;

    public RepoMock() throws IOException, URISyntaxException {
        new UnZiper().act(ZIP, REPO);
        String workDir = TEST_WORK_DIR + "tmp/repo/";
        git = new Git(new FileRepository(new File(workDir + "/.git")));
        configureRemote(git);
    }

    public void close() throws Exception {
        remoteRepo.close();
        delete(REPO);
    }

    private void configureRemote(Git git) throws URISyntaxException, IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin" ,"fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("remote", "origin" ,"push", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("branch", "master", "remote", "origin");
        config.setString("branch", "master", "merge", "refs/heads/master");
        config.setString("push", null, "default", "current");
        RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
        URIish uri = new URIish(RemoteRepoMock.REPO_URL);
        remoteConfig.addURI(uri);
        remoteConfig.addFetchRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.addPushRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.update(config);
        config.save();
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

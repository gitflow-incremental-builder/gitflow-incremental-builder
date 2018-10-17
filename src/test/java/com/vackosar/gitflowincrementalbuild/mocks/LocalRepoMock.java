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
import java.nio.file.Files;

public class LocalRepoMock implements AutoCloseable {

    private File baseFolder;
    private File templateProjectZip = new File(getClass().getClassLoader().getResource("template.zip").getFile());
    private RemoteRepoMock remoteRepo;
    private Git git;

    public LocalRepoMock(File baseFolder, boolean withRemote) throws IOException, URISyntaxException, GitAPIException {
        this.baseFolder = new File(baseFolder.getAbsolutePath(), "tmp/repo/");
        new UnZipper().act(templateProjectZip, this.baseFolder);

        Files.move(this.baseFolder.toPath().resolve("wrkf2"), baseFolder.toPath().resolve("wrkf2"));

        remoteRepo = new RemoteRepoMock(baseFolder, false);
        git = new Git(new FileRepository(new File(this.baseFolder, ".git")));

        if (withRemote) {
            configureRemote(git, remoteRepo.repoUrl);
        }
    }

    public Git getGit() {
        return git;
    }

    private void configureRemote(Git git, String repoUrl) throws URISyntaxException, IOException, GitAPIException {
        StoredConfig config = git.getRepository().getConfig();
        config.clear();
        config.setString("remote", "origin" ,"fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("remote", "origin" ,"push", "+refs/heads/*:refs/remotes/origin/*");
        config.setString("branch", "master", "remote", "origin");
        config.setString("baseBranch", "master", "merge", "refs/heads/master");
        config.setString("push", null, "default", "current");

        RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
        URIish uri = new URIish(repoUrl);
        remoteConfig.addURI(uri);
        remoteConfig.addFetchRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.addPushRefSpec(new RefSpec("refs/heads/master:refs/heads/master"));
        remoteConfig.update(config);

        config.save();
        git.fetch().call();
    }

    @Override
    public void close() throws Exception {
        remoteRepo.close();
        git.getRepository().close();
        git.close();
    }

    public RemoteRepoMock getRemoteRepo() {
        return remoteRepo;
    }

    public File getBaseCanonicalBaseFolder() throws IOException {
        return baseFolder.getCanonicalFile();
    }

}

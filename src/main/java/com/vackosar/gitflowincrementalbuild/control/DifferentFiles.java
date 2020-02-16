package com.vackosar.gitflowincrementalbuild.control;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;

import org.apache.maven.execution.MavenSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
@Named
public class DifferentFiles {

    public static final String UNSUPPORTED_WORKTREE = "JGit unsupported separate worktree checkout detected from current git dir path: ";

    private static final String HEAD = "HEAD";
    private static final String REFS_REMOTES = "refs/remotes/";
    private static final String REFS_HEADS = "refs/heads/";

    private Logger logger = LoggerFactory.getLogger(DifferentFiles.class);

    @Inject private MavenSession mavenSession;
    @Inject private Configuration.Provider configProvider;

    private final Map<String, String> additionalNativeGitEnvironment = new HashMap<>();

    public Set<Path> get() throws GitAPIException, IOException {
        Set<Path> paths = new HashSet<>();

        Configuration configuration = configProvider.get();
        Git git = setupGit(configuration);
        Worker worker = null;
        try {
            worker = new Worker(git, configuration);

            worker.fetch();
            worker.checkout();
            if (!configuration.disableBranchComparison) {
                paths.addAll(worker.getBranchDiff());
            }
            if (configuration.uncommited || configuration.untracked) {
                paths.addAll(worker.getChangesFromStatus());
            }
        } finally {
            if (worker != null) {
                worker.credentialsProvider.resetAll();
            }
            git.getRepository().close();
            git.close();
        }
        return paths;
    }

    /**
     * Only for testing!
     *
     * @param key environment key for {@link DelegatingCredentialsProvider#lookupCredentials(URIish)}
     * @param value environment value for {@link DelegatingCredentialsProvider#lookupCredentials(URIish)}
     */
    void putAdditionalNativeGitEnvironment(String key, String value) {
        additionalNativeGitEnvironment.put(key, value);
    }

    private Git setupGit(Configuration configuration) throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File pomDir = mavenSession.getCurrentProject().getBasedir().toPath().toFile();
        builder.findGitDir(pomDir);
        if (builder.getGitDir() == null) {
            String gitDirNotFoundMessage = "Git repository root directory not found ascending from current working directory:'" + pomDir + "'.";
            logger.warn(gitDirNotFoundMessage + " Next step is determined by failOnMissingGitDir property.");
            if (configuration.failOnMissingGitDir) {
                throw new IllegalArgumentException(gitDirNotFoundMessage);
            } else {
                throw new SkipExecutionException(gitDirNotFoundMessage);
            }
        }
        if (isWorktree(builder)) {
            throw new SkipExecutionException(UNSUPPORTED_WORKTREE + builder.getGitDir());
        }
        return Git.wrap(builder.build());
    }

    private boolean isWorktree(FileRepositoryBuilder builder) {
        Path gitDirParent = builder.getGitDir().toPath().getParent();
        return gitDirParent.getFileName().toString().equals("worktrees")
                && gitDirParent.getParent().getFileName().toString().equals(".git");
    }

    private class Worker {

        private final Git git;
        private final Path workTree;
        private final Configuration configuration;
        private final DelegatingCredentialsProvider credentialsProvider;

        public Worker(Git git, Configuration configuration) {
            this.git = git;
            this.workTree = git.getRepository().getWorkTree().toPath().normalize().toAbsolutePath();
            this.configuration = configuration;
            this.credentialsProvider = new DelegatingCredentialsProvider(workTree);
        }

        private Set<Path> getBranchDiff() throws IOException {
            RevCommit base = getBranchCommit(configuration.baseBranch);
            final TreeWalk treeWalk = new TreeWalk(git.getRepository());
            try {
                treeWalk.addTree(base.getTree());
                treeWalk.addTree(resolveReference(base).getTree());
                treeWalk.setFilter(TreeFilter.ANY_DIFF);
                treeWalk.setRecursive(true);
                return getDiff(treeWalk, workTree);
            } finally {
                treeWalk.close();
            }
        }

        private void checkout() throws IOException, GitAPIException {
            if (! (HEAD.equals(configuration.baseBranch) || configuration.baseBranch.startsWith("worktrees/")) && ! git.getRepository().getFullBranch().equals(configuration.baseBranch)) {
                logger.info("Checking out base branch " + configuration.baseBranch + "...");
                git.checkout().setName(configuration.baseBranch).call();
            }
        }

        private void fetch() throws GitAPIException {
            if (!configuration.disableBranchComparison && configuration.fetchReferenceBranch) {
                fetch(configuration.referenceBranch);
            }
            if (configuration.fetchBaseBranch) {
                fetch(configuration.baseBranch);
            }
        }

        private void fetch(String branchName) throws GitAPIException {
            logger.info("Fetching branch " + branchName);
            if (!branchName.startsWith(REFS_REMOTES)) {
                throw new IllegalArgumentException("Branch name '" + branchName + "' is not tracking branch name since it does not start " + REFS_REMOTES);
            }
            String remoteName = extractRemoteName(branchName);
            String shortName = extractShortName(remoteName, branchName);
            git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(remoteName)
                    .setRefSpecs(new RefSpec(REFS_HEADS + shortName + ":" + branchName))
                    .call();
        }

        private String extractRemoteName(String branchName) {
            return branchName.split("/")[2];
        }

        private String extractShortName(String remoteName, String branchName) {
            return branchName.replaceFirst(REFS_REMOTES + remoteName + "/", "");
        }

        private RevCommit getMergeBase(RevCommit baseCommit, RevCommit referenceHeadCommit) throws IOException {
            RevWalk walk = new RevWalk(git.getRepository());
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.lookupCommit(baseCommit));
            walk.markStart(walk.lookupCommit(referenceHeadCommit));
            RevCommit commit = walk.next();
            walk.close();
            logger.info("Using merge base of id: " + commit.getId());
            return commit;
        }

        private Set<Path> getDiff(TreeWalk treeWalk, Path gitDir) throws IOException {
            final Set<Path> paths = new HashSet<>();
            while (treeWalk.next()) {
                Path path = Paths.get(treeWalk.getPathString()).normalize();
                if (pathNotExcluded(path)) {
                    paths.add(gitDir.resolve(path));
                }
            }
            return paths;
        }

        private RevCommit getBranchCommit(String branchName) throws IOException {
            ObjectId objectId = git.getRepository().resolve(branchName);

            if (objectId == null) {
                throw new IllegalArgumentException("Git branch of name '" + branchName + "' not found.");
            }
            final RevWalk walk = new RevWalk(git.getRepository());
            RevCommit commit = walk.parseCommit(objectId);
            walk.close();
            logger.info("Reference commit of branch " + branchName + " is commit of id: " + commit.getId());
            return commit;
        }

        private Set<Path> getChangesFromStatus() throws GitAPIException {
            Set<String> changes = new HashSet<>();
            Status status = git.status().call();
            if (configuration.uncommited) {
                changes.addAll(status.getUncommittedChanges());
            }
            if (configuration.untracked) {
                changes.addAll(status.getUntracked());
            }
            return changes.stream()
                    .map(Paths::get)
                    .map(Path::normalize)
                    .filter(this::pathNotExcluded)
                    .map(workTree::resolve)
                    .collect(Collectors.toSet());
        }

        private RevCommit resolveReference(RevCommit base) throws IOException {
            RevCommit refHead = getBranchCommit(configuration.referenceBranch);
            if (configuration.compareToMergeBase) {
                return getMergeBase(base, refHead);
            } else {
                return refHead;
            }
        }

        private boolean pathNotExcluded(Path path) {
            boolean excluded = configuration.excludePathRegex.test(path.toString());
            logger.debug("excluded {}: {}", excluded, path);
            return !excluded;
        }
    }

    /**
     * JGit-{@link CredentialsProvider} for HTTP(S) that is delegating all credential requests to native Git via {@code git credential fill}. This will consult
     * all configured credential helpers, if any (for the repo, the user and the system). Such a helper might query the user for the credentials in case it
     * cannot yet provide them. However, the assumption here is that the credentials should already exist. Therefore this provider does <i>not</i> give feedback
     * to native Git via {@code git credential approve} or {@code git credential verify}.<p/>
     * This provider will suppress any console input requests (see
     * <a href="https://git-scm.com/docs/git#Documentation/git.txt-codeGITTERMINALPROMPTcode">GIT_TERMINAL_PROMPT</a>).
     *
     * @see <a href="https://git-scm.com/docs/git-credential">Git documentation: git credential</a>
     */
    private class DelegatingCredentialsProvider extends CredentialsProvider {

        private Logger logger = LoggerFactory.getLogger(DelegatingCredentialsProvider.class);

        private final Path projectDir;

        private final Map<URIish, CredentialsPair> credentials = new HashMap<>();

        public DelegatingCredentialsProvider(Path projectDir) {
            this.projectDir = projectDir;
        }

        @Override
        public boolean isInteractive() {
            // possibly interactive in case some credential helper asks for input
            return true;
        }

        @Override
        public boolean supports(CredentialItem... items) {
            return Arrays.stream(items)
                    .allMatch(item -> item instanceof CredentialItem.Username || item instanceof CredentialItem.Password);
        }

        @Override
        public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {

            // only handle HTTP(s)
            if (uri.getScheme() != null && !uri.getScheme().startsWith("http")) {
                return false;
            }

            CredentialsPair credentialsPair = credentials.computeIfAbsent(uri, u -> {
                try {
                    return lookupCredentials(uri);
                } catch (IOException | InterruptedException | RuntimeException e) {
                    logger.warn("Failed to look up credentials via 'git credential fill' for: " + uri, e);
                    return null;
                }
            });
            if (credentialsPair == null) {
                return false;
            }

            // map extracted credentials to CredentialItems, see also: org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
            for (CredentialItem item : items) {
                if (item instanceof CredentialItem.Username) {
                    ((CredentialItem.Username) item).setValue(credentialsPair.username);
                } else if (item instanceof CredentialItem.Password) {
                    ((CredentialItem.Password) item).setValue(credentialsPair.password);
                } else if (item instanceof CredentialItem.StringType && item.getPromptText().equals("Password: ")) {
                    ((CredentialItem.StringType) item).setValue(new String(credentialsPair.password));
                } else {
                    throw new UnsupportedCredentialItem(uri, item.getClass().getName() + ":" + item.getPromptText());
                }
            }

            return true;
        }

        @Override
        // see also: org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider.clear()
        public void reset(URIish uri) {
            Optional.ofNullable(credentials.remove(uri))
                    .ifPresent(credPair -> {
                        credPair.username = null;
                        Arrays.fill(credPair.password, (char) 0);
                        credPair.password = null;
                    });
        }

        public void resetAll() {
            new HashSet<>(credentials.keySet()).forEach(this::reset);
        }

        private CredentialsPair lookupCredentials(URIish uri) throws IOException, InterruptedException {
            // utilize JGit command execution capabilities
            FS fs = FS.detect();
            ProcessBuilder procBuilder = fs.runInShell("git", new String[] {"credential", "fill"});

            // prevent native git from requesting console input (not implemented)
            procBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");

            // add additional environment entries, if present (test only)
            if (!additionalNativeGitEnvironment.isEmpty()) {
                procBuilder.environment().putAll(additionalNativeGitEnvironment);
            }
            procBuilder.directory(projectDir.toFile());

            ExecutionResult result = fs.execute(procBuilder, new ByteArrayInputStream(buildGitCommandInput(uri).getBytes()));
            if (result.getRc() != 0) {
                logger.info(bufferToString(result.getStdout()));
                logger.error(bufferToString(result.getStderr()));
                throw new IllegalStateException("Native Git invocation failed with return code " + result.getRc()
                        + ". See previous log output for more details.");
            }

            return extractCredentials(bufferToString(result.getStdout()));
        }

        // build input for "git credential fill" as per https://git-scm.com/docs/git-credential#_typical_use_of_git_credential
        private String buildGitCommandInput(URIish uri) {
            StringBuilder builder = new StringBuilder();
            builder.append("protocol=").append(uri.getScheme()).append("\n");
            builder.append("host=").append(uri.getHost());
            if (uri.getPort() != -1) {
                builder.append(":").append(uri.getPort());
            }
            builder.append("\n");
            Optional.ofNullable(uri.getPath())
                    .map(path -> path.startsWith("/") ? path.substring(1) : path)
                    .ifPresent(path -> builder.append("path=").append(path).append("\n"));
            Optional.ofNullable(uri.getUser())
                    .ifPresent(user -> builder.append("username=").append(user).append("\n"));
            return builder.toString();
        }

        private String bufferToString(TemporaryBuffer buffer) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            buffer.writeTo(baos, null);
            return baos.toString();
        }

        private CredentialsPair extractCredentials(String nativeGitOutput) {
            Matcher matcher = Pattern.compile("(?<=username=).+|(?<=password=).+").matcher(nativeGitOutput);
            if (!matcher.find()) {
                throw new IllegalStateException("Could not find username in native Git output");
            }
            String username = matcher.group();
            if (!matcher.find()) {
                throw new IllegalStateException("Could not find password in native Git output");
            }
            char[] password = matcher.group().toCharArray();

            CredentialsPair credPair = new CredentialsPair();
            credPair.username = username;
            credPair.password = password;
            return credPair;
        }

        private class CredentialsPair {
            private String username;
            private char[] password;
        }
    }
}

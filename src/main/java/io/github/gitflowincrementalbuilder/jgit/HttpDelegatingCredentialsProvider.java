package io.github.gitflowincrementalbuilder.jgit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JGit-{@link CredentialsProvider} for HTTP(S) that is delegating all credential requests to native Git via {@code git credential fill}. This will consult
 * all configured credential helpers, if any (for the repo, the user and the system). Such a helper might query the user for the credentials in case it
 * cannot yet provide them. However, the assumption here is that the credentials should already exist. Therefore this provider does <i>not</i> give feedback
 * to native Git via {@code git credential approve} or {@code git credential verify}.
 * <p>
 * This provider will suppress any console input requests (see
 * <a href="https://git-scm.com/docs/git#Documentation/git.txt-codeGITTERMINALPROMPTcode">GIT_TERMINAL_PROMPT</a>).
 * </p>
 *
 * @see <a href="https://git-scm.com/docs/git-credential">Git documentation: git credential</a>
 */
class HttpDelegatingCredentialsProvider extends CredentialsProvider {

    private Logger logger = LoggerFactory.getLogger(HttpDelegatingCredentialsProvider.class);

    private final Path projectDir;
    private final Map<String, String> additionalNativeGitEnvironment;

    private final Map<URIish, CredentialsPair> credentials = new HashMap<>();

    public HttpDelegatingCredentialsProvider(Path projectDir, Map<String, String> additionalNativeGitEnvironment) {
        this.projectDir = projectDir;
        this.additionalNativeGitEnvironment = new HashMap<>(additionalNativeGitEnvironment);
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

        ExecutionResult result = fs.execute(procBuilder, new ByteArrayInputStream(buildGitCommandInput(uri).getBytes(Charset.defaultCharset())));
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
        return baos.toString(Charset.defaultCharset().name());
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

    private static class CredentialsPair {
        private String username;
        private char[] password;
    }
}
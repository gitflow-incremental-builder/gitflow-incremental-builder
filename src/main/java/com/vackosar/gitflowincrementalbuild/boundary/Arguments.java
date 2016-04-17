package com.vackosar.gitflowincrementalbuild.boundary;

import org.apache.commons.cli.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class Arguments {

    public static final String OPT_KEY = "k";
    public static final String OPT_REFERENCE_BRANCH = "rb";
    public static final String OPT_BRANCH = "b";
    public static final String DEFAULT_REFERENCE_BRANCH = "refs/remotes/origin/develop";
    public static final String DEFAULT_BRANCH = "HEAD";
    public final Path pom;
    public final Optional<Path> key;
    public final String referenceBranch;
    public final String branch;

    @Inject
    public Arguments(String[] args, Path workDir) throws IOException {
        CommandLine line;
        try {
            line = createCommandLine(args);
            checkArgSize(line);
            pom = parsePom(workDir, line);
            key = parseKey(workDir, line);
            referenceBranch = parseReferenceBranch(line);
            branch = parseBranch(line);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("[path to pom] [OPTIONS]", createOptions());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    private String parseBranch(CommandLine line) {
        final String value = line.getOptionValue(OPT_BRANCH);
        if (value != null) {
            return value;
        } else {
            return DEFAULT_REFERENCE_BRANCH;
        }
    }

    private String parseReferenceBranch(CommandLine line) {
        final String value = line.getOptionValue(OPT_REFERENCE_BRANCH);
        if (value != null) {
            return value;
        } else {
            return DEFAULT_BRANCH;
        }
    }

    private CommandLine createCommandLine(String[] args) throws ParseException {
        Options options = createOptions();
        CommandLineParser parser = new BasicParser();
        return parser.parse(options, args);
    }

    private Options createOptions() {
        return new Options()
                .addOption(OptionBuilder.hasArg(true).withArgName("path").withLongOpt("key").withDescription("path to repo private key").create(OPT_KEY))
                .addOption(OptionBuilder.hasArg(true).withArgName("reference branch").withLongOpt("reference-branch").withDescription("defaults to '" + DEFAULT_REFERENCE_BRANCH + "'").create(OPT_REFERENCE_BRANCH))
                .addOption(OptionBuilder.hasArg(true).withArgName("branch").withLongOpt("branch").withDescription("defaults to '" + DEFAULT_BRANCH + "'").create(OPT_BRANCH));
    }

    private void checkArgSize(CommandLine line) throws ParseException {
        if (line.getArgs().length != 1) { throw new ParseException("More than one argument.");}
    }

    private Path parsePom(Path workDir, CommandLine line) throws IOException {
        return workDir.resolve(line.getArgs()[0]).toAbsolutePath().toRealPath().normalize();
    }

    private Optional<Path> parseKey(Path workDir, CommandLine line) throws IOException {
        String keyOptionValue = line.getOptionValue(OPT_KEY);
        if (keyOptionValue != null) {
            return Optional.of(workDir.resolve(keyOptionValue).toAbsolutePath().toRealPath().normalize());
        } else {
            return Optional.empty();
        }
    }
}

package com.vackosar.gitflowincrementalbuild.boundary;

import org.apache.commons.cli.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class Arguments {

    public final Path pom;
    public final Optional<Path> key;

    @Inject
    public Arguments(String[] args, Path workDir) throws IOException {
        CommandLine line;
        try {
            line = createCommandLine(args);
            checkArgSize(line);
            pom = parsePom(workDir, line);
            key = parseKey(workDir, line);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("[path to pom] [OPTIONS]", createOptions());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    private CommandLine createCommandLine(String[] args) throws ParseException {
        Options options = createOptions();
        CommandLineParser parser = new BasicParser();
        return parser.parse(options, args);
    }

    private Options createOptions() {
        return new Options().addOption(OptionBuilder.hasArg(true).withArgName("key file path").withLongOpt("key").create("k"));
    }

    private void checkArgSize(CommandLine line) throws ParseException {
        if (line.getArgs().length != 1) { throw new ParseException("More than one argument.");}
    }

    private Path parsePom(Path workDir, CommandLine line) throws IOException {
        return workDir.resolve(line.getArgs()[0]).toAbsolutePath().toRealPath().normalize();
    }

    private Optional<Path> parseKey(Path workDir, CommandLine line) throws IOException {
        String keyOptionValue = line.getOptionValue("k");
        if (keyOptionValue != null) {
            return Optional.of(workDir.resolve(keyOptionValue).toAbsolutePath().toRealPath().normalize());
        } else {
            return Optional.empty();
        }
    }
}

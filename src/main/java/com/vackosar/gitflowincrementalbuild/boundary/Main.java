package com.vackosar.gitflowincrementalbuild.boundary;

import com.google.inject.Guice;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws GitAPIException, IOException {
        Guice
                .createInjector(new Module(args))
                .getInstance(Executor.class)
                .act();
    }
}

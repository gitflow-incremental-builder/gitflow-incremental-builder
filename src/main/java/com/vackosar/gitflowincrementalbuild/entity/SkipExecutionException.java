package com.vackosar.gitflowincrementalbuild.entity;

public class SkipExecutionException extends RuntimeException {

    public SkipExecutionException(String msg) {
        super(msg);
    }
}

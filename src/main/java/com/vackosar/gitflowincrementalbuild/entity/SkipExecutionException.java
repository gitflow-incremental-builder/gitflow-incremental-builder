package com.vackosar.gitflowincrementalbuild.entity;

@SuppressWarnings("serial")
public class SkipExecutionException extends RuntimeException {

    public SkipExecutionException(String msg) {
        super(msg);
    }
}

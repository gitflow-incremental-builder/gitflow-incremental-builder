package io.github.gitflowincrementalbuilder.entity;

@SuppressWarnings("serial")
public class SkipExecutionException extends RuntimeException {

    public SkipExecutionException(String msg) {
        super(msg);
    }
}

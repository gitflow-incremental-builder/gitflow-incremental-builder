package io.github.gitflowincrementalbuilder;

@SuppressWarnings("serial")
public class SkipExecutionException extends RuntimeException {

    public SkipExecutionException(String msg) {
        super(msg);
    }
}

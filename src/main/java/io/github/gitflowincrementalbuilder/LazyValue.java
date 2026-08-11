package io.github.gitflowincrementalbuilder;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lazy value that is initialized on first access. Not thread-safe and for internal use only.
 */
public class LazyValue<T> implements Supplier<T> {

    private T value;
    private Supplier<T> initializer;

    public LazyValue(Supplier<T> initializer) {
        this.initializer = Objects.requireNonNull(initializer);
    }

    @Override
    public T get() {
        if (initializer != null) {
            value = initializer.get();
            initializer = null;
        }
        return value;
    }
}
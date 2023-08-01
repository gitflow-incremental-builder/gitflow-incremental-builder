package io.github.gitflowincrementalbuilder;

import java.util.function.Supplier;

class LazyValue<T> implements Supplier<T> {

    private T value;
    private final Supplier<T> initializer;

    public LazyValue(Supplier<T> initializer) {
        this.initializer = initializer;
    }

    @Override
    public T get() {
        if (value == null) {
            value = initializer.get();
        }
        return value;
    }
}
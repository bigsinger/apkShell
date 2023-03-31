package com.bigsing.shellapp;

import android.content.Context;
import android.content.ContextWrapper;

public class CustomContextWrapper extends ContextWrapper {
    private final ClassLoader classLoader;

    public CustomContextWrapper(Context base, ClassLoader classLoader) {
        super(base);
        this.classLoader = classLoader;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}

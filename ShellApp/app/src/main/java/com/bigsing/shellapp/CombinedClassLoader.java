package com.bigsing.shellapp;

import java.net.URL;

public class CombinedClassLoader extends ClassLoader {

    private final ClassLoader pathClassLoader;
    private final ClassLoader inMemoryDexClassLoader;

    public CombinedClassLoader(ClassLoader pathClassLoader, ClassLoader inMemoryDexClassLoader) {
        super(null);
        this.pathClassLoader = pathClassLoader;
        this.inMemoryDexClassLoader = inMemoryDexClassLoader;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return inMemoryDexClassLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            // Fallback to the original PathClassLoader if the class is not found in InMemoryDexClassLoader
            return pathClassLoader.loadClass(name);
        }
    }

    @Override
    public URL getResource(String name) {
        URL resource = inMemoryDexClassLoader.getResource(name);
        if (resource == null) {
            resource = pathClassLoader.getResource(name);
        }
        return resource;
    }
}

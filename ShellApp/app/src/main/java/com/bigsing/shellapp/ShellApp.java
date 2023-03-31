package com.bigsing.shellapp;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

import dalvik.system.InMemoryDexClassLoader;


public class ShellApp extends Application {
    public static final String TAG = ShellApp.class.getSimpleName();

    // 被加固的dex在assets目录下的文件名
    private static final String ENCRYPTED_DEX_NAME = "encrypted_dex.dex";

    // 被加固的dex的解压密码（或解密密码），实际上本demo中未使用
    private static final String ZIP_PASSWORD = "123456";

    // 外壳的包名
    private static final String SHELL_PACKAGE_ID = "com.bigsing.shellapp";

    // 缓存读取大小
    private static final int BUFFER_SIZE = 4096;

    private String originAppClassName;

    private Class<?> originAppClazz;
    private Application originApp;

    private CustomContextWrapper customContextWrapper;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (isRunningInHostApp()) {
            // 宿主APP运行环境
            originAppClassName = getOriginAppViaMetaData();
            if (originAppClassName == null) {
                originAppClassName = getOriginAppViaAssetFile();
            }

            if (originAppClassName == null) {
                originAppClassName = getOriginAppViaConfigClass();
            }

            if (originAppClassName != null) {
                // Load and start original Application
                Log.d(TAG, "Original Application class: " + originAppClassName);
                loadDex(base, originAppClassName);
                attachBaseContextOfOriginApp(originApp);
            } else {
                Log.e(TAG, "Unable to find the original Application class.");
            }
        } else {
            // 自测工程运行环境
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (originApp != null) {
            originApp.onCreate();
        }

        // Set the CombinedClassLoader as the context class loader for the current thread
        if (customContextWrapper != null) {
            //Thread.currentThread().setContextClassLoader(customContextWrapper.getClassLoader());
            try {
                // Replace the ActivityThread's class loader with the CombinedClassLoader
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
                currentActivityThreadMethod.setAccessible(true);
                Object activityThread = currentActivityThreadMethod.invoke(null);

                Field mPackagesField = activityThreadClass.getDeclaredField("mPackages");
                mPackagesField.setAccessible(true);
                Map<String, ?> mPackages = (Map<String, ?>) mPackagesField.get(activityThread);

                WeakReference<?> wr = (WeakReference<?>) mPackages.get(getPackageName());
                Object loadedApk = wr.get();

                Field mClassLoaderField = loadedApk.getClass().getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, customContextWrapper.getClassLoader());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //originApp.attachBaseContext(base);
    private void attachBaseContextOfOriginApp(Context base) {
        if (originApp != null) {
            try {
                Method attachBaseContextMethod = originAppClazz.getDeclaredMethod("attachBaseContext", Context.class);
                attachBaseContextMethod.setAccessible(true);
                attachBaseContextMethod.invoke(originApp, customContextWrapper);
            } catch (Exception e) {
                throw new RuntimeException("Failed to attach base context to the original Application", e);
            }
        }
    }

    private boolean isRunningInHostApp() {
        String packageName = getPackageName();
        //return true;
        return !packageName.equals(SHELL_PACKAGE_ID);
    }

    private void loadDex(Context context, String originAppClassName) {
        try {
            ByteBuffer decryptedDex = decryptDex(context, ZIP_PASSWORD);
            loadDecryptedDex(context, decryptedDex, originAppClassName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer decryptDex(Context context, String password) throws IOException {
        AssetManager assetManager = context.getAssets();

        try (InputStream is = assetManager.open(ENCRYPTED_DEX_NAME)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            return ByteBuffer.wrap(os.toByteArray());
        } catch (IOException e) {
            throw new IOException("Failed to load the DEX file from assets", e);
        }
    }

    private void loadDecryptedDex(Context context, ByteBuffer dexBytes, String originAppClassName) {
        InMemoryDexClassLoader inMemoryDexClassLoader = new InMemoryDexClassLoader(dexBytes, getClassLoader());
        ClassLoader combinedClassLoader = new CombinedClassLoader(getClassLoader(), inMemoryDexClassLoader);

        try {
            originAppClazz = combinedClassLoader.loadClass(originAppClassName);
            originApp = (Application) originAppClazz.newInstance();

            // Create a new CustomContextWrapper with the CombinedClassLoader
            customContextWrapper = new CustomContextWrapper(context, combinedClassLoader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getOriginAppViaMetaData() {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = ai.metaData;
            if (metaData != null) {
                String originAppClassName = metaData.getString("originApp");
                if (originAppClassName != null) {
                    Log.d(TAG, "Original Application class from meta-data: " + originAppClassName);
                    return originAppClassName;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error while reading meta-data: " + e.getMessage());
        }
        return null;
    }

    private String getOriginAppViaAssetFile() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("shell_config")));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("originApp=")) {
                    String originAppClassName = line.substring(10);
                    Log.d(TAG, "Original Application class from asset file: " + originAppClassName);
                    return originAppClassName;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while reading asset file: " + e.getMessage());
        }
        return null;
    }

    private String getOriginAppViaConfigClass() {
        try {
            Class<?> configClass = Class.forName("com.bigsing.shell.config");
            Field originAppField = configClass.getField("originApp");
            String originAppClassName = (String) originAppField.get(null);
            Log.d(TAG, "Original Application class from config class: " + originAppClassName);
            return originAppClassName;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Error while reading config class: " + e.getMessage());
        }
        return null;
    }
}

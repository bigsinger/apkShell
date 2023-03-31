package com.bigsing.shelltest;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class MyApp extends Application {
    public static final String TAG = MyApp.class.getSimpleName();


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG, TAG + ".attachBaseContext");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, TAG + ".onCreate");
    }
}

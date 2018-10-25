package me.linjw.plugindemo;

import android.app.Application;

/**
 * Created by linjw on 18-10-8.
 */

public class MyApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        HookHelper.init(this);
    }
}

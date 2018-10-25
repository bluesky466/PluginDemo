package me.linjw.plugindemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by linjw on 18-10-8.
 */

public class HookHelper {
    public static void init(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hookActivityManagerAndroidO();
        } else {
            hookActivityManager();
        }
        hookActivityThread();
        hookPackageManager(context);
    }

    private static void hookActivityManager() {
        try {
            // 获取gDefault
            Class activityManagerClass = Class.forName("android.app.ActivityManagerNative");
            Field gDefaultField = activityManagerClass.getDeclaredField("gDefault");
            gDefaultField.setAccessible(true);
            Object gDefault = gDefaultField.get(null);

            //　获取mIntance
            Class singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            Object mInstance = mInstanceField.get(gDefault);

            // 替换mIntance
            Object proxy = Proxy.newProxyInstance(
                    mInstance.getClass().getClassLoader(),
                    new Class[]{Class.forName("android.app.IActivityManager")},
                    new IActivityManagerHandler(mInstance));
            mInstanceField.set(gDefault, proxy);

        } catch (Exception e) {
            Log.e("hook", "err", e);
        }
    }


    private static void hookActivityManagerAndroidO() {
        try {
            /// 获取IActivityManagerSingleton
            Class activityManagerClass = Class.forName("android.app.ActivityManager");
            Field singletonField = activityManagerClass.getDeclaredField("IActivityManagerSingleton");
            singletonField.setAccessible(true);
            Object gDefault = singletonField.get(null);

            //　获取mIntance
            Class singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            Object mInstance = mInstanceField.get(gDefault);

            // 替换mIntance
            Object proxy = Proxy.newProxyInstance(
                    mInstance.getClass().getClassLoader(),
                    new Class[]{Class.forName("android.app.IActivityManager")},
                    new IActivityManagerHandler(mInstance));
            mInstanceField.set(gDefault, proxy);

        } catch (Exception e) {
            Log.e("hook", "err", e);
        }
    }

    private static void hookActivityThread() {
        try {
            //　获取ActivityThread实例
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Field threadField = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            threadField.setAccessible(true);
            Object sCurrentActivityThread = threadField.get(null);

            //　获取mH变量
            Field mHField = activityThreadClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            final Object mH = mHField.get(sCurrentActivityThread);

            //　设置mCallback变量
            Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            Handler.Callback callback = new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    if (msg.what == 100) {
                        try {
                            Field intentField = msg.obj.getClass().getDeclaredField("intent");
                            intentField.setAccessible(true);
                            Intent intent = (Intent) intentField.get(msg.obj);
                            Intent raw = intent.getParcelableExtra("RawIntent");
                            intent.setComponent(raw.getComponent());
                        } catch (Exception e) {
                            Log.e("hook", "get intent err", e);
                        }

                    }
                    return false;
                }
            };
            mCallbackField.set(mH, callback);
        } catch (Exception e) {
            Log.e("hook", "err", e);
        }
    }

    private static void hookPackageManager(Context context){
        try {
            //要先获取一下,保证它初始化
            context.getPackageManager();

            Class activityThread = Class.forName("android.app.ActivityThread");
            Field pmField = activityThread.getDeclaredField("sPackageManager");
            pmField.setAccessible(true);
            final Object origin = pmField.get(null);
            Object handler = Proxy.newProxyInstance(activityThread.getClassLoader(),
                    new Class[]{Class.forName("android.content.pm.IPackageManager")},
                    new PackageManagerHandler(context, origin));
            pmField.set(null, handler);
        } catch (Exception e) {
            Log.e("hook", "hook IPackageManager err", e);
        }
    }


    static class PackageManagerHandler implements InvocationHandler {
        private Context mContext;
        private Object mOrigin;

        PackageManagerHandler(Context context, Object origin) {
            mContext = context;
            mOrigin = origin;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!method.getName().equals("getActivityInfo")) {
                return method.invoke(mOrigin, args);
            }

            //如果没有注册,并不会抛出异常,而是会直接返回null
            Object ret = method.invoke(mOrigin, args);
            if (ret == null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ComponentName) {
                        ComponentName componentName = (ComponentName) args[i];
                        componentName.getClassName();
                        args[i] = new ComponentName(
                                mContext.getPackageName(),
                                StubActivity.class.getName()
                        );
                        return method.invoke(mOrigin, args);
                    }
                }
            }
            return ret;

        }
    }

    public static class IActivityManagerHandler implements InvocationHandler {
        private Object mOrigin;

        IActivityManagerHandler(Object origin) {
            mOrigin = origin;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("startActivity".equals(method.getName())) {
                int index = 0;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        index = i;
                        break;
                    }
                }
                Intent raw = (Intent) args[index];

                Intent intent = new Intent();
                intent.setClassName(raw.getComponent().getPackageName(), StubActivity.class.getName());
                intent.putExtra("RawIntent", raw);
                args[index] = intent;
            }
            return method.invoke(mOrigin, args);
        }
    }
}

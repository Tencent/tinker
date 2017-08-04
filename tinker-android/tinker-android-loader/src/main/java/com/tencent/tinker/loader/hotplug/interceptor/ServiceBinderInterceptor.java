package com.tencent.tinker.loader.hotplug.interceptor;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class ServiceBinderInterceptor extends Interceptor<IBinder> {
    private static final String TAG = "Tinker.SrvBndrIntrcptr";
    private static final ClassLoader MY_CLASSLOADER = ServiceBinderInterceptor.class.getClassLoader();

    private final String mServiceName;
    private final BinderInvocationHandler mBinderInvocationHandler;

    private static Class<?> sServiceManagerClazz = null;
    private static Field sSCacheField = null;
    private static Method sGetServiceMethod = null;

    static {
        synchronized (ServiceBinderInterceptor.class) {
            if (sServiceManagerClazz == null) {
                try {
                    sServiceManagerClazz = Class.forName("android.os.ServiceManager");
                    sSCacheField = ShareReflectUtil.findField(sServiceManagerClazz, "sCache");
                    sGetServiceMethod = ShareReflectUtil.findMethod(sServiceManagerClazz, "getService", String.class);
                } catch (Throwable thr) {
                    Log.e(TAG, "unexpected exception.", thr);
                }
            }
        }
    }

    public ServiceBinderInterceptor(String serviceName, BinderInvocationHandler binderInvocationHandler) {
        mServiceName = serviceName;
        mBinderInvocationHandler = binderInvocationHandler;
    }

    @Override
    protected IBinder fetchTarget() throws Throwable {
        return (IBinder) sGetServiceMethod.invoke(null, mServiceName);
    }

    @Override
    protected IBinder decorate(IBinder target) throws Throwable {
        return (IBinder) Proxy.newProxyInstance(MY_CLASSLOADER, target.getClass().getInterfaces(),
                new FakeClientBinderHandler(mBinderInvocationHandler, target));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void inject(IBinder decorated) throws Throwable {
        final Map<String, IBinder> sCache = (Map<String, IBinder>) sSCacheField.get(null);
        sCache.put(mServiceName, decorated);
        if (Context.ACTIVITY_SERVICE.equals(mServiceName)) {
            clearAMSBinderCache();
        } else if ("package".equals(mServiceName)) {
            clearPMSBinderCache();
        }
    }

    private static void clearAMSBinderCache() throws Throwable {
        final Class<?> amsNativeClazz = Class.forName("android.app.ActivityManagerNative");
        final Field gDefaultField = ShareReflectUtil.findField(amsNativeClazz, "gDefault");
        final Object gDefault = gDefaultField.get(null);
        final Field mInstanceField = ShareReflectUtil.findField(gDefault, "mInstance");
        mInstanceField.set(gDefault, null);
    }

    private static void clearPMSBinderCache() throws Throwable {
        final Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
        final Field sPackageManagerField = ShareReflectUtil.findField(
                activityThreadClazz, "sPackageManager");
        sPackageManagerField.set(null, null);
    }

    public interface BinderInvocationHandler {
        Object invoke(Object target, Method method, Object[] args) throws Throwable;
    }

    private static class FakeClientBinderHandler implements InvocationHandler {
        private final BinderInvocationHandler mBinderInvocationHandler;
        private final IBinder mRealClientBinder;

        private IInterface mRealLocalInterface = null;

        FakeClientBinderHandler(BinderInvocationHandler binderInvocationHandler, IBinder realClientBinder) {
            mBinderInvocationHandler = binderInvocationHandler;
            mRealClientBinder = realClientBinder;
        }

        @Override
        public Object invoke(Object fakeClientBinder, Method method, Object[] args) throws Throwable {
            if ("queryLocalInterface".equals(method.getName())) {
                if (mRealLocalInterface == null) {
                    final String itfName = mRealClientBinder.getInterfaceDescriptor();
                    String stubClassName = null;
                    if (itfName.equals("android.app.IActivityManager")) {
                        stubClassName = "android.app.ActivityManagerNative";
                    } else {
                        stubClassName = itfName + "$Stub";
                    }
                    Class<?> stubClazz = Class.forName(stubClassName);
                    final Method asInterfaceMethod = ShareReflectUtil.findMethod(stubClazz, "asInterface", IBinder.class);
                    mRealLocalInterface = (IInterface) asInterfaceMethod.invoke(null, mRealClientBinder);
                }
                final InvocationHandler fakeLocalInterfaceHandler = new FakeIInterfaceHandler(
                        mBinderInvocationHandler, (IBinder) fakeClientBinder, mRealLocalInterface);
                return Proxy.newProxyInstance(MY_CLASSLOADER, mRealLocalInterface.getClass().getInterfaces(),
                        fakeLocalInterfaceHandler);
            } else {
                return method.invoke(mRealClientBinder, args);
            }
        }
    }

    private static class FakeIInterfaceHandler implements InvocationHandler {
        private final BinderInvocationHandler mBinderInvocationHandler;
        private final IBinder mFakeClientBinder;
        private final IInterface mRealIInterface;

        FakeIInterfaceHandler(BinderInvocationHandler binderInvocationHandler,
                              IBinder fakeClientBinder, IInterface readIInterface) {
            mBinderInvocationHandler = binderInvocationHandler;
            mFakeClientBinder = fakeClientBinder;
            mRealIInterface = readIInterface;
        }

        @Override
        public Object invoke(Object fakeIInterface, Method method, Object[] args) throws Throwable {
            if ("asBinder".equals(method.getName())) {
                return mFakeClientBinder;
            } else {
                return mBinderInvocationHandler.invoke(mRealIInterface, method, args);
            }
        }
    }
}

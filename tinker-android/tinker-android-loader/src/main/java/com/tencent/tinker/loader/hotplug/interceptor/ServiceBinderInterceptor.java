package com.tencent.tinker.loader.hotplug.interceptor;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class ServiceBinderInterceptor extends Interceptor<IBinder> {
    private static final String TAG = "Tinker.SvcBndrIntrcptr";

    private final Context mBaseContext;
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

    public ServiceBinderInterceptor(Context context, String serviceName, BinderInvocationHandler binderInvocationHandler) {
        while (context != null && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        mBaseContext = context;
        mServiceName = serviceName;
        mBinderInvocationHandler = binderInvocationHandler;
    }

    @Override
    protected IBinder fetchTarget() throws Throwable {
        return (IBinder) sGetServiceMethod.invoke(null, mServiceName);
    }

    @Override
    protected IBinder decorate(IBinder target) throws Throwable {
        if (target == null) {
            throw new IllegalStateException("target is null.");
        }
        if (ITinkerHotplugProxy.class.isAssignableFrom(target.getClass())) {
            // Already intercepted, just return the target.
            return target;
        } else {
            return createProxy(getAllInterfacesThroughDeriveChain(target.getClass()),
                    new FakeClientBinderHandler(target, mBinderInvocationHandler));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void inject(IBinder decorated) throws Throwable {
        final Map<String, IBinder> sCache = (Map<String, IBinder>) sSCacheField.get(null);
        sCache.put(mServiceName, decorated);
        if (Context.ACTIVITY_SERVICE.equals(mServiceName)) {
            fixAMSBinderCache(decorated);
        } else if (EnvConsts.PACKAGE_MANAGER_SRVNAME.equals(mServiceName)) {
            fixPMSBinderCache(mBaseContext, decorated);
        }
    }

    private static void fixAMSBinderCache(IBinder fakeBinder) throws Throwable {
        Object singletonObj = null;
        try {
            final Class<?> amsNativeClazz = Class.forName("android.app.ActivityManagerNative");
            final Field gDefaultField = ShareReflectUtil.findField(amsNativeClazz, "gDefault");
            singletonObj = gDefaultField.get(null);
        } catch (Throwable thr) {
            final Class<?> amClazz = Class.forName("android.app.ActivityManager");
            final Field iActivityManagerSingletonField = ShareReflectUtil.findField(amClazz, "IActivityManagerSingleton");
            singletonObj = iActivityManagerSingletonField.get(null);
        }

        final Field mInstanceField = ShareReflectUtil.findField(singletonObj, "mInstance");
        final IInterface originalInterface = (IInterface) mInstanceField.get(singletonObj);

        if (originalInterface == null || ITinkerHotplugProxy.class.isAssignableFrom(originalInterface.getClass())) {
            return;
        }

        final IInterface fakeInterface = fakeBinder.queryLocalInterface(fakeBinder.getInterfaceDescriptor());
        if (fakeInterface == null || !ITinkerHotplugProxy.class.isAssignableFrom(fakeInterface.getClass())) {
            throw new IllegalStateException("fakeBinder does not return fakeInterface, binder: " + fakeBinder + ", itf: " + fakeInterface);
        }
        mInstanceField.set(singletonObj, fakeInterface);
    }

    private static void fixPMSBinderCache(Context context, IBinder fakeBinder) throws Throwable {
        final Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
        final Field sPackageManagerField = ShareReflectUtil.findField(activityThreadClazz, "sPackageManager");
        final IInterface originalInterface = (IInterface) sPackageManagerField.get(null);
        if (originalInterface != null && !ITinkerHotplugProxy.class.isAssignableFrom(originalInterface.getClass())) {
            final IInterface fakeInterface = fakeBinder.queryLocalInterface(fakeBinder.getInterfaceDescriptor());
            if (fakeInterface == null || !ITinkerHotplugProxy.class.isAssignableFrom(fakeInterface.getClass())) {
                throw new IllegalStateException("fakeBinder does not return fakeInterface, binder: " + fakeBinder + ", itf: " + fakeInterface);
            }
            sPackageManagerField.set(null, fakeInterface);
        }

        final Class<?> applicationPackageManagerClazz = Class.forName("android.app.ApplicationPackageManager");
        final Field mPMField = ShareReflectUtil.findField(applicationPackageManagerClazz, "mPM");
        final PackageManager pm = context.getPackageManager();
        final IInterface originalInterface2 = (IInterface) mPMField.get(pm);
        if (originalInterface2 != null && !ITinkerHotplugProxy.class.isAssignableFrom(originalInterface2.getClass())) {
            final IInterface fakeInterface = fakeBinder.queryLocalInterface(fakeBinder.getInterfaceDescriptor());
            if (fakeInterface == null || !ITinkerHotplugProxy.class.isAssignableFrom(fakeInterface.getClass())) {
                throw new IllegalStateException("fakeBinder does not return fakeInterface, binder: " + fakeBinder + ", itf: " + fakeInterface);
            }
            mPMField.set(pm, fakeInterface);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<?>[] itfs, InvocationHandler handler) {
        final Class<?>[] mergedItfs = new Class<?>[itfs.length + 1];
        System.arraycopy(itfs, 0, mergedItfs, 0, itfs.length);
        mergedItfs[itfs.length] = ITinkerHotplugProxy.class;
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
            return (T) Proxy.newProxyInstance(cl, mergedItfs, handler);
        } catch (Throwable thr) {
            final Set<ClassLoader> uniqueCls = new HashSet<>(4);
            for (Class<?> itf : mergedItfs) {
                uniqueCls.add(itf.getClassLoader());
            }
            if (uniqueCls.size() == 1) {
                cl = uniqueCls.iterator().next();
            } else {
                cl = new ClassLoader() {
                    @Override
                    protected Class<?> loadClass(String className, boolean resolve)
                            throws ClassNotFoundException {
                        Class<?> res = null;
                        for (ClassLoader cl : uniqueCls) {
                            try {
                                // fix some device PathClassLoader behind BootClassLoader which lead to ClassNotFoundException
                                res = cl.loadClass(className);
                            } catch (Throwable ignore) {

                            }
                            if (res != null) {
                                return res;
                            }
                        }
                        throw new ClassNotFoundException("cannot find class: " + className);
                    }
                };
            }
            try {
                return (T) Proxy.newProxyInstance(cl, mergedItfs, handler);
            } catch (Throwable thr2) {
                throw new RuntimeException("cl: " + cl, thr);
            }
        }
    }

    private static Class<?>[] getAllInterfacesThroughDeriveChain(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        final Set<Class<?>> itfs = new HashSet<>(10);
        while (!Object.class.equals(clazz)) {
            itfs.addAll(Arrays.asList(clazz.getInterfaces()));
            clazz = clazz.getSuperclass();
        }
        return itfs.toArray(new Class<?>[itfs.size()]);
    }

    public interface BinderInvocationHandler {
        Object invoke(Object target, Method method, Object[] args) throws Throwable;
    }

    private static class FakeClientBinderHandler implements InvocationHandler {
        private final BinderInvocationHandler mBinderInvocationHandler;
        private final IBinder mOriginalClientBinder;

        FakeClientBinderHandler(IBinder originalClientBinder, BinderInvocationHandler binderInvocationHandler) {
            mOriginalClientBinder = originalClientBinder;
            mBinderInvocationHandler = binderInvocationHandler;
        }

        @Override
        public Object invoke(Object fakeClientBinder, Method method, Object[] args) throws Throwable {
            if ("queryLocalInterface".equals(method.getName())) {
                final String itfName = mOriginalClientBinder.getInterfaceDescriptor();
                String stubClassName = null;
                if (itfName.equals("android.app.IActivityManager")) {
                    stubClassName = "android.app.ActivityManagerNative";
                } else {
                    stubClassName = itfName + "$Stub";
                }
                final Class<?> stubClazz = Class.forName(stubClassName);
                final Method asInterfaceMethod
                        = ShareReflectUtil.findMethod(stubClazz, "asInterface", IBinder.class);

                final IInterface originalInterface
                        = (IInterface) asInterfaceMethod.invoke(null, mOriginalClientBinder);

                final InvocationHandler fakeInterfaceHandler
                        = new FakeInterfaceHandler(originalInterface, (IBinder) fakeClientBinder, mBinderInvocationHandler);

                return createProxy(getAllInterfacesThroughDeriveChain(originalInterface.getClass()), fakeInterfaceHandler);
            } else {
                return method.invoke(mOriginalClientBinder, args);
            }
        }
    }

    private static class FakeInterfaceHandler implements InvocationHandler {
        private final BinderInvocationHandler mBinderInvocationHandler;
        private final IBinder mOriginalClientBinder;
        private final IInterface mOriginalInterface;

        FakeInterfaceHandler(IInterface originalInterface, IBinder originalClientBinder,
                             BinderInvocationHandler binderInvocationHandler) {
            mOriginalInterface = originalInterface;
            mOriginalClientBinder = originalClientBinder;
            mBinderInvocationHandler = binderInvocationHandler;
        }

        @Override
        public Object invoke(Object fakeIInterface, Method method, Object[] args) throws Throwable {
            if ("asBinder".equals(method.getName())) {
                return mOriginalClientBinder;
            } else {
                return mBinderInvocationHandler.invoke(mOriginalInterface, method, args);
            }
        }
    }
}

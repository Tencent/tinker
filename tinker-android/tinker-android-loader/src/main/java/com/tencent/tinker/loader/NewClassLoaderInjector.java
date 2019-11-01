package com.tencent.tinker.loader;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by tomystang on 2019-10-31.
 */
public final class NewClassLoaderInjector {
    private static final String TAG = "NewClassLoaderInjector";

    private static final class DispatchClassLoader extends ClassLoader {
        private final String mApplicationClassName;
        private final ClassLoader mOldClassLoader;

        DispatchClassLoader(String applicationClassName, ClassLoader oldClassLoader) {
            super(oldClassLoader.getParent());
            mApplicationClassName = applicationClassName;
            mOldClassLoader = oldClassLoader;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(mApplicationClassName)) {
                return mOldClassLoader.loadClass(name);
            } else if (name.startsWith("com.tencent.tinker.loader.")
                    && !name.equals(SystemClassLoaderAdder.CHECK_DEX_CLASS)) {
                return mOldClassLoader.loadClass(name);
            } else if (name.startsWith("org.apache.commons.codec.")
                    || name.startsWith("org.apache.commons.logging.")
                    || name.startsWith("org.apache.http.")) {
                return mOldClassLoader.loadClass(name);
            } else {
                // This invocation will throw ClassNotFoundException for any class names.
                // Then we will fallback to NewClassLoader's findClass next.
                return super.findClass(name);
            }
        }
    }

    public static ClassLoader inject(Application app, ClassLoader oldClassLoader) throws Throwable {
        final ClassLoader dispatchClassLoader
                = new DispatchClassLoader(app.getClass().getName(), oldClassLoader);
        final ClassLoader newClassLoader
                = createNewClassLoader(oldClassLoader, dispatchClassLoader);
        doInject(app, newClassLoader);
        return newClassLoader;
    }

    @SuppressWarnings("unchecked")
    private static ClassLoader createNewClassLoader(ClassLoader oldClassLoader,
                                                    ClassLoader dispatchClassLoader) throws Throwable {
        final Field pathListField = findField(BaseDexClassLoader.class, "pathList");
        final Object oldPathList = pathListField.get(oldClassLoader);

        final Field dexElementsField = findField(oldPathList.getClass(), "dexElements");
        final Object[] oldDexElements = (Object[]) dexElementsField.get(oldPathList);

        final Field nativeLibraryDirectoriesField = findField(oldPathList.getClass(), "nativeLibraryDirectories");
        final List<File> oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);

        final Field dexFileField = findField(oldDexElements.getClass().getComponentType(), "dexFile");

        final StringBuilder dexPathBuilder = new StringBuilder();

        boolean isFirstItem = true;
        for (Object oldDexElement : oldDexElements) {
            String dexPath = null;
            final DexFile dexFile = (DexFile) dexFileField.get(oldDexElement);
            if (dexFile != null) {
                dexPath = dexFile.getName();
            }
            if (dexPath == null || dexPath.isEmpty()) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                dexPathBuilder.append(File.pathSeparator);
            }
            dexPathBuilder.append(dexPath);
        }

        final String combinedDexPath = dexPathBuilder.toString();

        final StringBuilder libraryPathBuilder = new StringBuilder();
        isFirstItem = true;
        for (File libDir : oldNativeLibraryDirectories) {
            if (libDir == null) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                libraryPathBuilder.append(File.pathSeparator);
            }
            libraryPathBuilder.append(libDir.getAbsolutePath());
        }

        final String combinedLibraryPath = libraryPathBuilder.toString();

        final ClassLoader result = new PathClassLoader(combinedDexPath, combinedLibraryPath, oldClassLoader.getParent());
        findField(result.getClass(), "parent").set(result, dispatchClassLoader);
        return result;
    }

    private static void doInject(Application app, ClassLoader classLoader) throws Throwable {
        Thread.currentThread().setContextClassLoader(classLoader);

        final Context baseContext = (Context) findField(app.getClass(), "mBase").get(app);
        final Object basePackageInfo = findField(baseContext.getClass(), "mPackageInfo").get(baseContext);
        findField(basePackageInfo.getClass(), "mClassLoader").set(basePackageInfo, classLoader);

        if (Build.VERSION.SDK_INT < 27) {
            final Resources res = app.getResources();
            findField(res.getClass(), "mClassLoader").set(res, classLoader);

            final Object drawableInflater = findField(res.getClass(), "mDrawableInflater").get(res);
            if (drawableInflater != null) {
                findField(drawableInflater.getClass(), "mClassLoader").set(drawableInflater, classLoader);
            }
        }
    }

    private static Field findField(Class<?> clazz, String name) throws Throwable {
        Class<?> currClazz = clazz;
        while (true) {
            try {
                final Field result = currClazz.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (Throwable ignored) {
                if (currClazz == Object.class) {
                    throw new NoSuchFieldException("Cannot find field "
                            + name + " in class " + clazz.getName() + " and its super classes.");
                } else {
                    currClazz = currClazz.getSuperclass();
                }
            }
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... argTypes) throws Throwable {
        Class<?> currClazz = clazz;
        while (true) {
            try {
                final Method result = currClazz.getDeclaredMethod(name, argTypes);
                result.setAccessible(true);
                return result;
            } catch (Throwable ignored) {
                if (currClazz == Object.class) {
                    throw new NoSuchMethodException("Cannot find method "
                            + name + " " + Arrays.toString(argTypes)
                            + " in class " + clazz.getName() + " and its super classes.");
                } else {
                    currClazz = currClazz.getSuperclass();
                }
            }
        }
    }

    private NewClassLoaderInjector() {}
}

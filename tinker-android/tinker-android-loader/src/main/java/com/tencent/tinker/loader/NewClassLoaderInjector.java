package com.tencent.tinker.loader;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import com.tencent.tinker.loader.shareutil.ShareConstants;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

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
        private ClassLoader mNewClassLoader;
        private final int mGPExpansionMode;
        private final ThreadLocal<Boolean> mFallThroughToNewCL = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return false;
            }
        };

        DispatchClassLoader(Application app, ClassLoader oldClassLoader, int gpExpansionMode) {
            super(oldClassLoader.getParent());
            mApplicationClassName = app.getClass().getName();
            mOldClassLoader = oldClassLoader;
            mGPExpansionMode = gpExpansionMode;
        }

        void setNewClassLoader(ClassLoader newClassLoader) {
            mNewClassLoader = newClassLoader;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (mFallThroughToNewCL.get()) {
                // Goto NewClassLoader directly since we are here the second time
                // now.
                return null;
            }
            if (name.equals(mApplicationClassName)) {
                return mOldClassLoader.loadClass(name);
            } else if (name.startsWith("com.tencent.tinker.loader.")
                    && !name.equals(SystemClassLoaderAdder.CHECK_DEX_CLASS)) {
                if (mGPExpansionMode != ShareConstants.TINKER_GPMODE_DISABLE
                        && name.startsWith("com.tencent.tinker.loader.shareutil.")) {
                    // We will fallback to NewClassLoader's findClass here and
                    // not to try OldClassLoader.
                    return null;
                } else {
                    return mOldClassLoader.loadClass(name);
                }
            } else if (name.startsWith("org.apache.commons.codec.")
                    || name.startsWith("org.apache.commons.logging.")
                    || name.startsWith("org.apache.http.")) {
                return mOldClassLoader.loadClass(name);
            } else {
                mFallThroughToNewCL.set(true);
                try {
                    return mNewClassLoader.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // Some class cannot find in NewClassLoader should try OldClassLoader again.
                    return mOldClassLoader.loadClass(name);
                } finally {
                    mFallThroughToNewCL.set(false);
                }
            }
        }
    }

    public static ClassLoader inject(Application app, ClassLoader oldClassLoader, int gpExpansionMode) throws Throwable {
        final DispatchClassLoader dispatchClassLoader
                = new DispatchClassLoader(app, oldClassLoader, gpExpansionMode);
        final ClassLoader newClassLoader
                = createNewClassLoader(app, oldClassLoader, dispatchClassLoader);
        dispatchClassLoader.setNewClassLoader(newClassLoader);
        doInject(app, newClassLoader);
        return newClassLoader;
    }

    @SuppressWarnings("unchecked")
    private static ClassLoader createNewClassLoader(Application app, ClassLoader oldClassLoader,
                                                    ClassLoader dispatchClassLoader) throws Throwable {
        final Class<?> baseDexClassLoaderClazz
                = Class.forName("dalvik.system.BaseDexClassLoader", false, oldClassLoader);
        final Field pathListField = findField(baseDexClassLoaderClazz, "pathList");
        final Object oldPathList = pathListField.get(oldClassLoader);

        final Field dexElementsField = findField(oldPathList.getClass(), "dexElements");
        final Object[] oldDexElements = (Object[]) dexElementsField.get(oldPathList);


        final Field dexFileField = findField(oldDexElements.getClass().getComponentType(), "dexFile");
        final StringBuilder dexPathBuilder = new StringBuilder();
        final String packageName = app.getPackageName();
        boolean isFirstItem = true;
        for (Object oldDexElement : oldDexElements) {
            String dexPath = null;
            final DexFile dexFile = (DexFile) dexFileField.get(oldDexElement);
            if (dexFile != null) {
                dexPath = dexFile.getName();
            }
            if (dexPath == null || dexPath.length() == 0) {
                continue;
            }
            if (!dexPath.contains("/" + packageName)) {
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

        final Field nativeLibraryDirectoriesField = findField(oldPathList.getClass(), "nativeLibraryDirectories");
        List<File> oldNativeLibraryDirectories = null;
        if (nativeLibraryDirectoriesField.getType().isArray()) {
            oldNativeLibraryDirectories = Arrays.asList((File[]) nativeLibraryDirectoriesField.get(oldPathList));
        } else {
            oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);
        }
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
        findField(oldPathList.getClass(), "definingContext").set(oldPathList, result);
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
            try {
                findField(res.getClass(), "mClassLoader").set(res, classLoader);

                final Object drawableInflater = findField(res.getClass(), "mDrawableInflater").get(res);
                if (drawableInflater != null) {
                    findField(drawableInflater.getClass(), "mClassLoader").set(drawableInflater, classLoader);
                }
            } catch (Throwable ignored) {
                // Ignored.
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

    private NewClassLoaderInjector() {}
}

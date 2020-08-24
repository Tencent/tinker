package com.tencent.tinker.loader;

import android.annotation.SuppressLint;

import com.tencent.tinker.anno.Keep;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import dalvik.system.BaseDexClassLoader;

import static com.tencent.tinker.loader.NewClassLoaderInjector.LOADER_CLASSNAME_PREFIX;

/**
 * Created by tangyinsheng on 2020-01-09.
 */
@Keep
@SuppressLint("NewApi")
public final class TinkerClassLoader extends BaseDexClassLoader {
    private final ClassLoader mOriginAppClassLoader;

    TinkerClassLoader(String dexPath, File optimizedDir, String libraryPath, ClassLoader originAppClassLoader) {
        super(dexPath, optimizedDir, libraryPath, ClassLoader.getSystemClassLoader());
        mOriginAppClassLoader = originAppClassLoader;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> cl = null;
        try {
            cl = super.findClass(name);
        } catch (ClassNotFoundException ignored) {
            cl = null;
        }
        if (cl != null) {
            return cl;
        } else {
            cl = mOriginAppClassLoader.loadClass(name);
        }
        if (cl == null) {
            throw new ClassNotFoundException(name);
        }
        if (cl.getClassLoader() != mOriginAppClassLoader) {
            return cl;
        } else if (name.startsWith(LOADER_CLASSNAME_PREFIX)) {
            return cl;
        } else {
            // Classes whose name does not start with loader class name prefix
            // and cannot be found in patched new dex should be reguard as
            // removed. So we skip looking them up in the original app ClassLoader here.
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    public URL getResource(String name) {
        // The lookup order we use here is the same as for classes.
        URL resource = Object.class.getClassLoader().getResource(name);
        if (resource != null) {
            return resource;
        }

        resource = findResource(name);
        if (resource != null) {
            return resource;
        }

        return mOriginAppClassLoader.getResource(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        final Enumeration<URL>[] resources =(Enumeration<URL>[]) new Enumeration<?>[]{
              Object.class.getClassLoader().getResources(name),
              findResources(name),
              mOriginAppClassLoader.getResources(name)
        };
        return new CompoundEnumeration<>(resources);
    }

    @Keep
    class CompoundEnumeration<E> implements Enumeration<E> {
        private Enumeration<E>[] enums;
        private int index = 0;

        public CompoundEnumeration(Enumeration<E>[] enums) {
            this.enums = enums;
        }

        @Override
        public boolean hasMoreElements() {
            while (index < enums.length) {
                if (enums[index] != null && enums[index].hasMoreElements()) {
                    return true;
                }
                index++;
            }
            return false;
        }

        @Override
        public E nextElement() {
            if (!hasMoreElements()) {
                throw new NoSuchElementException();
            }
            return enums[index].nextElement();
        }
    }
}

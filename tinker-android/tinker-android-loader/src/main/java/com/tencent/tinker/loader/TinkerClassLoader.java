package com.tencent.tinker.loader;

import android.annotation.SuppressLint;

import com.tencent.tinker.anno.Keep;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by tomystang on 2020-01-09.
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
            return mOriginAppClassLoader.loadClass(name);
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

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        @SuppressWarnings("unchecked")
        final Enumeration<URL>[] resources = (Enumeration<URL>[]) new Enumeration<?>[] {
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

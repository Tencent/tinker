/*
 * Copyright 2014 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader;

import android.annotation.TargetApi;
import android.os.Build;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by shwenzhang on 16/6/30.
 *
 * I think it is very amazing code here!
 * because delegateClassLoader's parent is PathClassLoader, and PathClassLoader's parent is IncrementalClassLoader.
 * All classes will search from
 * delegateClassLoader first, then PathClassLoader. It can ensure newly classes loading from delegateClassLoader.
 *
 * Reason:
 * 1) some rom such as Samsung s6 502, insert dexes at front of dexPathlist isn't work.
 * 2) Android N jit mode.
 * try as InstantRun and bazel (https://github.com/bazelbuild/bazel/blob/master/src/tools/android/
 * java/com/google/devtools/build/android/incrementaldeployment/IncrementalClassLoader.java)
 */
public class IncrementalClassLoader extends ClassLoader {
    private static final String TAG = "IncrementalClassLoader";

    private final DelegateClassLoader delegateClassLoader;

    public IncrementalClassLoader(BaseDexClassLoader original,
                                  String nativeLibraryPath, String optimizedDirectory, List<String> dexes) {
        super(original.getParent());

        // because delegateClassLoader's parent is PathClassLoader, and PathClassLoader's parent is IncrementalClassLoader. All classes will search from
        // delegateClassLoader first, then PathClassLoader. It can ensure newly classes loading from delegateClassLoader.
        // For some mysterious reason, we need to use two class loaders so that
        // everything works correctly. Investigate why that is the case so that the code can be
        // simplified.
        this.delegateClassLoader = createDelegateClassLoader(nativeLibraryPath,
            optimizedDirectory, dexes, original);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public Class<?> findClass(String className) throws ClassNotFoundException {
        return this.delegateClassLoader.findClass(className);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected String findLibrary(String libName) {
        return this.delegateClassLoader.findLibrary(libName);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class DelegateClassLoader extends BaseDexClassLoader {
        BaseDexClassLoader originClassLoader;

        private DelegateClassLoader(String dexPath, File optimizedDirectory,
                                    String libraryPath, BaseDexClassLoader parent) {
            super(dexPath, optimizedDirectory, libraryPath, parent);
            originClassLoader = parent;
        }

        public Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }

        /**
         * just use origin classloader find library, instead of reflect getLdLibraryPath
         * we don't add tinker lib path here for abi problem, we may optimize later.
         * @param name
         * @return
         */
        @Override
        public String findLibrary(String name) {
            return originClassLoader.findLibrary(name);
        }
    }

    private static DelegateClassLoader createDelegateClassLoader(
        String nativeLibraryPath, String optimizedDirectory, List<String> dexes,
        BaseDexClassLoader original) {
        String pathBuilder = createDexPath(dexes);
        return new DelegateClassLoader(pathBuilder, new File(optimizedDirectory),
            nativeLibraryPath, original);
    }

    private static String createDexPath(List<String> dexes) {
        StringBuilder pathBuilder = new StringBuilder();
        boolean first = true;
        for (String dex : dexes) {
            if (first) {
                first = false;
            } else {
                pathBuilder.append(":");
            }
            pathBuilder.append(dex);
        }

        return pathBuilder.toString();
    }

    private static void setParent(ClassLoader classLoader, ClassLoader newParent) throws Exception {
        Field parent = ClassLoader.class.getDeclaredField("parent");
        parent.setAccessible(true);
        parent.set(classLoader, newParent);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static ClassLoader inject(BaseDexClassLoader classLoader,
                                     String nativeLibraryPath, String optimizedDirectory, List<String> dexes) throws Exception {
        IncrementalClassLoader incrementalClassLoader = new IncrementalClassLoader(
            classLoader, nativeLibraryPath, optimizedDirectory, dexes);

        setParent(classLoader, incrementalClassLoader);
        return incrementalClassLoader;
    }
}

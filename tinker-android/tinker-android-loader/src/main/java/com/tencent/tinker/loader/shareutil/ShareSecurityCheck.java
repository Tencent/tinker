/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader.shareutil;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class ShareSecurityCheck {
    private static final String TAG = "ShareSecurityCheck";

    private static final String HASH_ALGORITHM = "SHA-1";
    private static Set<String> apkSignatureSet = new HashSet<>(1);

    private final HashMap<String, String> metaContentMap;
    private final HashMap<String, String> packageProperties;

    public ShareSecurityCheck(Context context) {
        metaContentMap = new HashMap<>();
        packageProperties = new HashMap<>();
        synchronized (ShareSecurityCheck.class) {
            if (apkSignatureSet.isEmpty()) {
                synchronized (ShareSecurityCheck.class) {
                    init(context);
                }
            }
        }
    }

    public HashMap<String, String> getMetaContentMap() {
        return metaContentMap;
    }

    /**
     * Nullable
     *
     * @return HashMap<String, String>
     */
    public HashMap<String, String> getPackagePropertiesIfPresent() {
        if (packageProperties.size() > 0) {
            return packageProperties;
        }

        final String property = metaContentMap.get(ShareConstants.PACKAGE_META_FILE);
        if (property == null) {
            return null;
        }

        for (String line : property.split("\n")) {
            if (line == null || line.length() <= 0) {
                continue;
            }
            //it is comment
            if (line.startsWith("#")) {
                continue;
            }
            final String[] kv = line.split("=", 2);
            if (kv.length < 2) {
                continue;
            }
            packageProperties.put(kv[0].trim(), kv[1].trim());
        }
        return packageProperties;
    }

    @SuppressLint("DefaultLocale")
    public boolean verifyPatchMetaSignature(File path) {
        if (!SharePatchFileUtil.isLegalFile(path)) {
            return false;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(path);
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                // no code
                if (jarEntry == null) {
                    continue;
                }

                final String name = jarEntry.getName();
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                //for faster, only check the meta.txt files
                //we will check other files's md5 written in meta files
                if (!name.endsWith(ShareConstants.META_SUFFIX)) {
                    continue;
                }
                metaContentMap.put(name, SharePatchFileUtil.loadDigestes(jarFile, jarEntry));
                Certificate[] certs = jarEntry.getCertificates();
                if (certs == null || !check(path, name, certs)) {
                    return false;
                }
            }
        } catch (Exception e) {
            throw new TinkerRuntimeException(
                String.format(
                    "ShareSecurityCheck file %s, size %d verifyPatchMetaSignature fail",
                    path.getAbsolutePath(), path.length()
                ), e);
        } finally {
            SharePatchFileUtil.closeQuietly(jarFile);
        }
        return true;
    }

    // verify the signature of the Apkg
    private boolean check(File path, String name, Certificate[] certs) throws NoSuchAlgorithmException {
        final MessageDigest sha1 = MessageDigest.getInstance(HASH_ALGORITHM);
        for (int i = 0; i < certs.length; ++i) {
            try {
                final byte[] encodedKey = certs[i].getEncoded();
                final String patchSignature = ShareHexEncoding.encode(sha1.digest(encodedKey));
                Log.d(TAG, String.format("#%d Patch(%s/%s) Signature is %s", i, path.getName(), name, patchSignature));
                if (apkSignatureSet.contains(patchSignature)) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, path.getAbsolutePath(), e);
            }
        }
        return false;
    }

    @SuppressLint("PackageManagerGetSignatures")
    private void init(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            for (int i = 0; i < packageInfo.signatures.length; ++i) {
                byte[] encodedKey = packageInfo.signatures[i].toByteArray();
                final MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
                final String currentSignature = ShareHexEncoding.encode(md.digest(encodedKey));
                Log.d(TAG, String.format("#%d APK(%s) Signature is %s", i, packageName, currentSignature));
                apkSignatureSet.add(currentSignature);
            }
        } catch (Exception e) {
            throw new TinkerRuntimeException("ShareSecurityCheck init signatures fail", e);
        }
    }
}

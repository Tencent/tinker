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

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class ShareSecurityCheck {
    private static final String TAG           = "Tinker.SecurityCheck";
    /**
     * static to faster
     * public key
     */
    private static       String mPublicKeyMd5 = null;

    private final Context                 mContext;
    private final HashMap<String, String> metaContentMap;
    private final HashMap<String, String> packageProperties;

    public ShareSecurityCheck(Context context) {
        mContext = context;
        metaContentMap = new HashMap<>();
        packageProperties = new HashMap<>();
        if (mPublicKeyMd5 == null) {
            init(mContext);
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
        if (!packageProperties.isEmpty()) {
            return packageProperties;
        }

        String property = metaContentMap.get(ShareConstants.PACKAGE_META_FILE);

        if (property == null) {
            return null;
        }

        String[] lines = property.split("\n");
        for (final String line : lines) {
            if (line == null || line.length() <= 0) {
                continue;
            }
            //it is comment
            if (line.startsWith("#")) {
                continue;
            }
            final String[] kv = line.split("=", 2);
            if (kv == null || kv.length < 2) {
                continue;
            }

            packageProperties.put(kv[0].trim(), kv[1].trim());
        }
        return packageProperties;
    }

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

                if (certs == null || !check(path, certs)) {
                    return false;
                }
            }
        } catch (Exception e) {
            throw new TinkerRuntimeException(
                String.format("ShareSecurityCheck file %s, size %d verifyPatchMetaSignature fail", path.getAbsolutePath(), path.length()), e);
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (IOException e) {
                ShareTinkerLog.e(TAG, path.getAbsolutePath(), e);
            }
        }
        return true;
    }


    // verify the signature of the Apk
    private boolean check(File path, Certificate[] certs) {
        if (certs.length > 0) {
            for (int i = certs.length - 1; i >= 0; i--) {
                try {
                    if (mPublicKeyMd5.equals(SharePatchFileUtil.getMD5(certs[i].getEncoded()))) {
                        return true;
                    }
                } catch (Exception e) {
                    ShareTinkerLog.e(TAG, path.getAbsolutePath(), e);
                }
            }
        }
        return false;
    }

    @SuppressLint("PackageManagerGetSignatures")
    private void init(Context context) {
        ByteArrayInputStream stream = null;
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            mPublicKeyMd5 = SharePatchFileUtil.getMD5(packageInfo.signatures[0].toByteArray());
            if (mPublicKeyMd5 == null) {
                throw new TinkerRuntimeException("get public key md5 is null");
            }
        } catch (Exception e) {
            throw new TinkerRuntimeException("ShareSecurityCheck init public key fail", e);
        } finally {
            SharePatchFileUtil.closeQuietly(stream);
        }
    }
}

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

package com.tencent.tinker.lib.patch;

import java.io.File;
import java.io.IOException;

public final class PatchEntryUtils {

    private PatchEntryUtils() {
    }

    public static boolean isValidEntryName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.contains("..")) {
            return false;
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return false;
        }
        if (name.contains(":")) {
            return false;
        }
        return true;
    }

    public static boolean isPathInDirectory(File file, File directory) throws IOException {
        final String canonicalDir = directory.getCanonicalPath();
        final String canonicalFile = file.getCanonicalPath();
        return canonicalFile.equals(canonicalDir)
                || canonicalFile.startsWith(canonicalDir + File.separator);
    }
}

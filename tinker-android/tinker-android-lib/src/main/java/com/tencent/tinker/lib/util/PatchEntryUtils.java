/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.lib.util;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;

public final class PatchEntryUtils {

    private PatchEntryUtils() {
    }

    public static File validateZipEntry(ZipEntry entry, File targetDir) throws IOException {
        if (entry == null) {
            throw new IOException("zip entry is null");
        }
        if (targetDir == null) {
            throw new IOException("target directory is null");
        }
        final File targetFile = new File(targetDir, entry.getName());
        final String targetDirCanonical = targetDir.getCanonicalPath();
        final String targetFileCanonical = targetFile.getCanonicalPath();
        if (!targetFileCanonical.startsWith(targetDirCanonical + File.separator)
                && !targetFileCanonical.equals(targetDirCanonical)) {
            throw new IOException("illegal zip entry path: " + entry.getName());
        }
        return targetFile;
    }
}

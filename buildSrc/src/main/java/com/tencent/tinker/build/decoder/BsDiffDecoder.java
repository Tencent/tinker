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

package com.tencent.tinker.build.decoder;

import com.tencent.tinker.bsdiff.BSDiff;
import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.Utils;

import java.io.File;
import java.io.IOException;

/**
 * Created by zhangshaowen on 16/2/27.
 */
public class BsDiffDecoder extends BaseDecoder {
    private final InfoWriter logWriter;
    private final InfoWriter metaWriter;

    public BsDiffDecoder(Configuration config, String metaPath, String logPath) throws IOException {
        super(config);

        if (metaPath != null) {
            metaWriter = new InfoWriter(config, config.mTempResultDir + File.separator + metaPath);
        } else {
            metaWriter = null;
        }

        if (logPath != null) {
            logWriter = new InfoWriter(config, config.mOutFolder + File.separator + logPath);
        } else {
            logWriter = null;
        }
    }

    @Override
    public void clean() {
        logWriter.close();
        metaWriter.close();
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        //first of all, we should check input files
        if (newFile == null || !newFile.exists()) {
            return false;
        }
        //new add file
        String newMd5 = MD5.getMD5(newFile);
        File bsDiffFile = getOutputPath(newFile).toFile();

        if (oldFile == null || !oldFile.exists()) {
            FileOperation.copyFileUsingStream(newFile, bsDiffFile);
            writeLogFiles(newFile, null, null, newMd5);
            return true;
        }

        //both file length is 0
        if (oldFile.length() == 0 && newFile.length() == 0) {
            return false;
        }
        if (oldFile.length() == 0 || newFile.length() == 0) {
            FileOperation.copyFileUsingStream(newFile, bsDiffFile);
            writeLogFiles(newFile, null, null, newMd5);
            return true;
        }

        //new add file
        String oldMd5 = MD5.getMD5(oldFile);

        if (oldMd5.equals(newMd5)) {
            return false;
        }

        if (!bsDiffFile.getParentFile().exists()) {
            bsDiffFile.getParentFile().mkdirs();
        }
        BSDiff.bsdiff(oldFile, newFile, bsDiffFile);

        if (Utils.checkBsDiffFileSize(bsDiffFile, newFile)) {
            writeLogFiles(newFile, oldFile, bsDiffFile, newMd5);
        } else {
            FileOperation.copyFileUsingStream(newFile, bsDiffFile);
            writeLogFiles(newFile, null, null, newMd5);
        }
        return true;
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }

    protected void writeLogFiles(File newFile, File oldFile, File bsDiff, String newMd5) throws IOException {
        if (metaWriter == null && logWriter == null) {
            return;
        }
        String parentRelative = getParentRelativePathStringToNewFile(newFile);
        String relative = getRelativePathStringToNewFile(newFile);

        if (metaWriter != null) {
            String fileName = newFile.getName();

            String meta;
            if (bsDiff == null || oldFile == null) {
                meta = fileName + "," + parentRelative + "," + newMd5 + "," + 0 + "," + 0;
            } else {
                String oldCrc = FileOperation.getZipEntryCrc(config.mOldApkFile, relative);
                if (oldCrc == null || oldCrc.equals("0")) {
                    throw new TinkerPatchException(
                        String.format("can't find zipEntry %s from old apk file %s", relative, config.mOldApkFile.getPath())
                    );
                }
                meta = fileName + "," + parentRelative + "," + newMd5 + "," + oldCrc + "," + MD5.getMD5(bsDiff);
            }
            Logger.d("BsDiffDecoder:write meta file data: %s", meta);
            metaWriter.writeLineToInfoFile(meta);
        }

        if (logWriter != null) {
            String log = relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                + FileOperation.getFileSizes(newFile) + ", diffSize=" + FileOperation.getFileSizes(bsDiff);

            logWriter.writeLineToInfoFile(log);
        }
    }
}

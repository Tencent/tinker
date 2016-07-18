/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
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
import com.tencent.tinker.build.util.TypedValue;

import java.io.File;
import java.io.IOException;

/**
 * Created by shwenzhang on 16/2/27.
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
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        //fist of all, we should check input files
        if (newFile == null) {
            return false;
        }
        //new add file
        String newMd5 = MD5.getMD5(newFile);
        File bsDiffFile = getOutputPath(newFile).toFile();

        if (oldFile == null) {
            FileOperation.copyFileUsingStream(newFile, bsDiffFile);
            writeLogFiles(newFile, null, null, newMd5, null);
            return true;
        }
        String oldMd5 = MD5.getMD5(oldFile);

        if (oldMd5.equals(newMd5)) {
            return false;
        }

        if (!bsDiffFile.getParentFile().exists()) {
            bsDiffFile.getParentFile().mkdirs();
        }
        BSDiff.bsdiff(oldFile, newFile, bsDiffFile);

        if (!bsDiffFile.exists()) {
            throw new TinkerPatchException("can not find the bsDiff file:" + bsDiffFile.getAbsolutePath());
        }
        //check bsDiffFile file size
        double ratio = bsDiffFile.length() / (double) newFile.length();
        if (ratio > TypedValue.BSDIFF_PATCH_MAX_RATIO) {
            Logger.e("bsDiff patch file:%s, size:%dk, new file:%s, size:%dk. patch file is too large, treat it as newly file to save patch time!",
                bsDiffFile.getName(),
                bsDiffFile.length() / 1024,
                newFile.getName(),
                newFile.length() / 1024
            );
            FileOperation.copyFileUsingStream(newFile, bsDiffFile);
            writeLogFiles(newFile, null, null, newMd5, null);
        } else {
            writeLogFiles(newFile, oldFile, bsDiffFile, newMd5, oldMd5);
        }
        return true;
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }

    protected void writeLogFiles(File newFile, File oldFile, File bsDiff, String newMd5, String oldMd5) throws IOException {

        if (metaWriter != null) {
            String parentRelative = getParentRelativeString(newFile);
            String fileName = newFile.getName();

            String meta;
            if (bsDiff == null || oldFile == null) {
                meta = fileName + "," + parentRelative + "," + newMd5 + "," + 0 + "," + 0;

            } else {
                meta = fileName + "," + parentRelative + "," + newMd5 + "," + oldMd5 + "," + MD5.getMD5(bsDiff);
            }
            Logger.d("bsDiffDecoder:write meta file data: %s", meta);
            metaWriter.writeLineToInfoFile(meta);
        }

        if (logWriter != null) {
            String relative = getRelativeString(newFile);

            String log = relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                + FileOperation.getFileSizes(newFile) + ", diffSize=" + FileOperation.getFileSizes(bsDiff);

            logWriter.writeLineToInfoFile(log);
        }
    }
}

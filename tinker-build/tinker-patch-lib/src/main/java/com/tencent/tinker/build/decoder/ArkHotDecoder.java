/*
 * Copyright (C) 2019. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the BSD 3-Clause License
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * the BSD 3-Clause License for more details.
 */

package com.tencent.tinker.build.decoder;

import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;

import java.io.File;
import java.io.IOException;

public class ArkHotDecoder extends BaseDecoder {
    private static final String ARKHOT_PATCH_NAME = "patch.apk";
    private static final String ARKHOT_PATCH_PATH = "arkHot";

    private final InfoWriter metaWriter;

    public ArkHotDecoder(Configuration config, String metaPath) throws IOException {
        super(config);

        if (metaPath != null) {
            metaWriter = new InfoWriter(config, config.mTempResultDir + File.separator + metaPath);
        } else {
            metaWriter = null;
        }
    }

    @Override
    public void clean() {
        metaWriter.close();
    }

    @Override
    public void onAllPatchesStart() {
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
        File patchFile = new File(config.mArkHotPatchPath + "/" + config.mArkHotPatchName);
        if (!patchFile.exists()) {
            return;
        }
        String md5 = MD5.getMD5(patchFile);

        File dest = new File(config.mTempResultDir + "/" + ARKHOT_PATCH_PATH + "/" + ARKHOT_PATCH_NAME);
        FileOperation.copyFileUsingStream(patchFile, dest);
        writeMetaFile(md5);
    }

    @Override
    public boolean patch(File oldFile, File newFile) {
        return true;
    }

    private void writeMetaFile(String md5) {
        if (metaWriter == null) {
            return;
        }

        if (metaWriter != null) {
            String path = ARKHOT_PATCH_PATH;
            String fileName = ARKHOT_PATCH_NAME;

            if (md5 == null) {
                return;
            }

            String meta = fileName + "," + path  +  "," + md5;
            metaWriter.writeLineToInfoFile(meta);
        }
    }
}

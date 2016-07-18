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

import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.TinkerPatchException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Created by shwenzhang on 16/2/28.
 */
public abstract class BaseDecoder {

    protected final Configuration config;
    protected final File          outDir;

    protected final File resultDir;


    public BaseDecoder(Configuration config) throws IOException {
        this.config = config;
        this.outDir = new File(config.mOutFolder);

        this.resultDir = config.mTempResultDir;

    }

    public Configuration getConfig() {
        return config;
    }


    protected void clean() {


    }

    public Path getRelativePath(File file) {
        return config.mTempUnzipNewDir.toPath().relativize(file.toPath());
    }

    public Path getOutputPath(File file) {
        return config.mTempResultDir.toPath().resolve(getRelativePath(file));
    }

    public String getRelativeString(File file) {
        return config.mTempUnzipNewDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
    }

    public String getParentRelativeString(File file) {
        return config.mTempUnzipNewDir.toPath().relativize(file.getParentFile().toPath()).toString().replace("\\", "/");
    }


    /**
     * 就算前后两个文件都是一样,也会交到这个文件夹
     *
     * @param oldFile 如果oldfile 为空，代表这是一个新的文件
     * @param newFile
     * @throws IOException
     * @throws TinkerPatchException
     */
    abstract public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException;

    abstract public void onAllPatchesStart() throws IOException, TinkerPatchException;

    abstract public void onAllPatchesEnd() throws IOException, TinkerPatchException;
}

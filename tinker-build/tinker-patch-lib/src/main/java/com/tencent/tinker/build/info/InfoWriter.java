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

package com.tencent.tinker.build.info;

import com.tencent.tinker.build.patch.Configuration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Created by shwenzhang on 16/3/8.
 */
public class InfoWriter {
    protected final Configuration config;
    /**
     * infoFile, output info
     */
    protected final String        infoPath;
    protected final File          infoFile;

    /**
     * 首次使用时初始化
     */
    protected Writer infoWrite;

    public InfoWriter(Configuration config, String infoPath) throws IOException {
        this.config = config;
        this.infoPath = infoPath;

        if (infoPath != null) {
            this.infoFile = new File(infoPath);
            if (!infoFile.getParentFile().exists()) {
                infoFile.getParentFile().mkdirs();
            }
        } else {
            this.infoFile = null;
        }

    }

    public Configuration getConfig() {
        return config;
    }


    public void writeLinesToInfoFile(List<String> lines) throws IOException {
        for (String line : lines) {
            writeLineToInfoFile(line);
        }
    }

    public void writeLineToInfoFile(String line) {
        if (infoPath == null || line == null || line.length() == 0) {
            return;
        }
        try {
            checkWriter();
            infoWrite.write(line);
            infoWrite.write("\n");
            infoWrite.flush();
        } catch (Exception e) {
            throw new RuntimeException("write info file error, infoPath:" + infoPath + " content:" + line, e);
        }
    }

    private void checkWriter() throws IOException {
        if (infoWrite == null) {
            this.infoWrite = new BufferedWriter(new FileWriter(infoFile, false));
        }

    }

    public void close() {
        try {
            if (infoWrite != null) infoWrite.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

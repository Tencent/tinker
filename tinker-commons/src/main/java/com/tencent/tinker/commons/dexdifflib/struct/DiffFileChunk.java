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

package com.tencent.tinker.commons.dexdifflib.struct;


import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;

import java.io.PrintWriter;

/**
 * Created by tomystang on 2016/4/14.
 */
public class DiffFileChunk<T extends SectionItem<T>> {
    public final byte                 type;
    public final PatchOpRecordList<T> patchOpList;

    public DiffFileChunk(byte type, PatchOpRecordList<T> patchOpList) {
        this.type = type;
        this.patchOpList = new PatchOpRecordList<>(patchOpList);
    }

    public void dump(PrintWriter pw) {
        if (pw == null) {
            pw = new PrintWriter(System.out);
        }
        pw.println("dump of difffile chunk:\n--------------------------------");
        pw.format("type: %d\n", type);
        int replaceCount = 0;
        for (PatchOpRecord<T> opRec : patchOpList) {
            pw.println(opRec);
            if (opRec.op == PatchOpRecord.OP_REPLACE) ++replaceCount;
        }
        pw.println(replaceCount);
        pw.println("--------------------------------");
        pw.format("Count: %d\n", patchOpList.size());
        pw.flush();
    }
}

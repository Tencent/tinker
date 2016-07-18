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

package com.tencent.tinker.commons.dexdifflib.algorithm;

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

public abstract class SectionPatchAlgorithm<T extends SectionItem<T>> extends ItemPatchAlgorithm<T> {
    private final Dex oldDex;
    private TableOfContents.Section oldToCSection = null;
    private Dex.Section             oldDexSection = null;

    protected SectionPatchAlgorithm(Dex oldDex, PatchOpRecordList<T> patchOpList) {
        super(patchOpList);
        this.oldDex = oldDex;
    }

    protected abstract TableOfContents.Section getToCSection(TableOfContents toc);

    protected abstract T readItemFromDexSection(Dex.Section section, int index);

    @Override
    protected T getOldItem(int index) {
        if (oldToCSection.isFourByteAlign) {
            oldDexSection.alignToFourBytes();
        }
        return readItemFromDexSection(oldDexSection, index);
    }

    @Override
    protected int getOldItemCount() {
        return oldToCSection.size;
    }

    @Override
    public SectionPatchAlgorithm<T> prepare() {
        this.oldToCSection = getToCSection(this.oldDex.getTableOfContents());
        if (this.oldToCSection.exists()) {
            this.oldDexSection = this.oldDex.open(this.oldToCSection);
        }
        super.prepare();
        return this;
    }

    @Override
    public SectionPatchAlgorithm<T> process() {
        super.process();
        return this;
    }
}

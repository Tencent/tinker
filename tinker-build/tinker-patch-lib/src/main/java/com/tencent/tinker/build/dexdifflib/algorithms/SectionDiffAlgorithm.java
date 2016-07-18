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

package com.tencent.tinker.build.dexdifflib.algorithms;

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

public abstract class SectionDiffAlgorithm<T extends SectionItem<T>> extends ItemDiffAlgorithm<T> {
    private Dex                     oldDex        = null;
    private Dex                     newDex        = null;
    private TableOfContents.Section oldToCSection = null;
    private TableOfContents.Section newToCSection = null;
    private Dex.Section             oldDexSection = null;
    private Dex.Section             newDexSection = null;

    public SectionDiffAlgorithm(Dex oldDex, Dex newDex, PatchOpRecordList<T> result) {
        super(result);
        this.oldDex = oldDex;
        this.newDex = newDex;
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
        return this.oldToCSection.size;
    }

    @Override
    protected T getNewItem(int index) {
        if (newToCSection.isFourByteAlign) {
            newDexSection.alignToFourBytes();
        }
        return readItemFromDexSection(newDexSection, index);
    }

    @Override
    protected int getNewItemCount() {
        return this.newToCSection.size;
    }

    @Override
    public SectionDiffAlgorithm prepare() {
        this.oldToCSection = getToCSection(this.oldDex.getTableOfContents());
        this.newToCSection = getToCSection(this.newDex.getTableOfContents());
        this.oldDexSection = (oldToCSection.exists() ? this.oldDex.open(this.oldToCSection) : null);
        this.newDexSection = (newToCSection.exists() ? this.newDex.open(this.newToCSection) : null);
        super.prepare();
        return this;
    }
}

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

import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.build.dexdifflib.util.PatternUtils;
import com.tencent.tinker.commons.dexdifflib.algorithm.AbstractAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created by tomystang on 2016/3/30.
 */
public class ClassExcludingAlgorithm extends AbstractAlgorithm {
    private final Dex newDex;

    private final Set<Integer> indexOfClassDefToExclude   = new HashSet<>();
    private final Set<Integer> offsetOfClassDataToExclude = new HashSet<>();

    private final Set<String>  namePatternOfClassToExcludeSet = new HashSet<>();
    private final Set<Integer> typeIndexOfClassToExcludeSet   = new HashSet<>();

    public ClassExcludingAlgorithm(Dex newDex, Collection<String> namePatternOfClassToExclude) {
        this.newDex = newDex;
        if (namePatternOfClassToExclude != null) {
            this.namePatternOfClassToExcludeSet.addAll(namePatternOfClassToExclude);
        }
    }

    @Override
    public ClassExcludingAlgorithm prepare() {
        // S1. Convert Classname pattern to descriptor RegExPattern
        List<Pattern> patternList = new ArrayList<>();
        for (String pattern : namePatternOfClassToExcludeSet) {
            String regEx = PatternUtils.dotClassNamePatternToDescriptorRegEx(pattern);
            Pattern regExPattern = Pattern.compile(regEx);
            patternList.add(regExPattern);
        }

        // S2. Filter class descriptors out to exclude using regEx pattern.
        for (ClassDef classDef : newDex.classDefs()) {
            String desc = newDex.typeNames().get(classDef.typeIndex);
            for (Pattern regExPattern : patternList) {
                if (regExPattern.matcher(desc).matches()) {
                    typeIndexOfClassToExcludeSet.add(classDef.typeIndex);
                }
            }
        }

        return this;
    }

    @Override
    public ClassExcludingAlgorithm process() {
        // S1. Convert name of classes to exclude to corresponding type index.
        // Then lookup and note all items that needs to be excluded.
        for (int typeIndex : typeIndexOfClassToExcludeSet) {
            int classDefIndex = newDex.findClassDefIndexFromTypeIndex(typeIndex);
            if (classDefIndex < 0) {
                continue;
            }
            ClassDef classDef = newDex.classDefsList().get(classDefIndex);
            if (classDef.classDataOffset > 0) {
                offsetOfClassDataToExclude.add(classDef.classDataOffset);
            }
            indexOfClassDefToExclude.add(classDefIndex);
        }
        return this;
    }

    public int getExcludedClassDataCount() {
        return offsetOfClassDataToExclude.size();
    }

    public boolean isClassDataOffsetExcluded(int offset) {
        return offsetOfClassDataToExclude.contains(offset);
    }

    public int getExcludedClassDefCount() {
        return indexOfClassDefToExclude.size();
    }

    public boolean isClassDefIndexExcluded(int index) {
        return indexOfClassDefToExclude.contains(index);
    }
}

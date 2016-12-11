package com.tencent.tinker.build.immutable;

import java.util.HashSet;
import java.util.Set;

public class DexRefData {
    int methodNum;
    int fieldNum;
    public Set<String> refFields;
    public Set<String> refMtds;

    DexRefData() {
        this(0, 0);
    }

    DexRefData(int methodNum, int fieldNum) {
        this.methodNum = methodNum;
        this.fieldNum = fieldNum;
        refFields = new HashSet<>();
        refMtds = new HashSet<>();
    }
}
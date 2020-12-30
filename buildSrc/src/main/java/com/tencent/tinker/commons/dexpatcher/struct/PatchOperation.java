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

package com.tencent.tinker.commons.dexpatcher.struct;

/**
 * Created by tangyinsheng on 2016/6/29.
 */
public final class PatchOperation<T> {
    public static final int OP_DEL = 0;
    public static final int OP_ADD = 1;
    public static final int OP_REPLACE = 2;

    public int op;
    public int index;
    public T newItem;

    public PatchOperation(int op, int index) {
        this(op, index, null);
    }

    public PatchOperation(int op, int index, T newItem) {
        this.op = op;
        this.index = index;
        this.newItem = newItem;
    }

    public static String translateOpToString(int op) {
        switch (op) {
            case OP_DEL:
                return "OP_DEL";
            case OP_ADD:
                return "OP_ADD";
            case OP_REPLACE:
                return "OP_REPLACE";
            default:
                return "OP_UNKNOWN";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String opDesc = translateOpToString(op);
        sb.append('{');
        sb.append("op: ").append(opDesc).append(", index: ").append(index).append(", newItem: ").append(newItem);
        sb.append('}');
        return sb.toString();
    }
}

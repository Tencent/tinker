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

package com.tencent.tinker.commons.dexpatcher.struct;

/**
 * Created by tomystang on 2016/6/29.
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String opDesc;
        switch (op) {
            case OP_DEL:
                opDesc = "OP_DEL";
                break;
            case OP_ADD:
                opDesc = "OP_ADD";
                break;
            case OP_REPLACE:
                opDesc = "OP_REPLACE";
                break;
            default:
                opDesc = "OP_UNKNOWN";
        }
        sb.append('{');
        sb.append("op: ").append(opDesc).append(", index: ").append(index).append(", newItem: ").append(newItem);
        sb.append('}');
        return sb.toString();
    }
}

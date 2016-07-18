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

public class PatchOpRecord<T> {
    public static final byte OP_DEL     = 0x00;
    public static final byte OP_ADD     = 0x01;
    public static final byte OP_REPLACE = 0x02;
    public static final byte OP_MOVE    = 0x03;

    public final byte op;
    public final int  oldIndex;
    public final int  newIndex;
    public final T    newItem;

    private PatchOpRecord(byte op, int oldIndex, int newIndex, T newItem) {
        this.op = op;
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
        this.newItem = newItem;
    }

    public static <T> PatchOpRecord<T> createAddOpRecord(int newIndex, T newItem) {
        return new PatchOpRecord<>(OP_ADD, newIndex, newIndex, newItem);
    }

    public static <T> PatchOpRecord<T> createDelOpRecord(int oldIndex) {
        return new PatchOpRecord<>(OP_DEL, oldIndex, oldIndex, null);
    }

    public static <T> PatchOpRecord<T> createReplaceOpRecord(int oldIndex, T newItem) {
        return new PatchOpRecord<>(OP_REPLACE, oldIndex, oldIndex, newItem);
    }

    public static <T> PatchOpRecord<T> createMoveOpRecord(int oldIndex, int newIndex) {
        return new PatchOpRecord<>(OP_MOVE, oldIndex, newIndex, null);
    }

    @Override
    public boolean equals(Object obj) {
        PatchOpRecord<T> o = (PatchOpRecord<T>) obj;
        if (op != o.op) {
            return false;
        }
        if (oldIndex != o.oldIndex) {
            return false;
        }
        if (newIndex != o.newIndex) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = op & 0xFF;
        result |= (oldIndex ^ (oldIndex << 16));
        result |= (newIndex ^ (newIndex >> 16));
        return result & 0x7FFFFFFF;
    }

    @SuppressWarnings("unchecked")
    @Override
    public String toString() {
//        String result = "{}";
//        switch (this.op) {
//            case OP_ADD: {
//                int oldItemOffset = (oldIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) oldIndexedItem.item).off : oldIndexedItem.index);
//                int newItemOffset = (newIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) newIndexedItem.item).off : newIndexedItem.index);
//                result = String.format("{op:add, oldIdx:%d, newIdx:%d, newVal:%s, oldOff:%d, newOff:%d}", oldIndexedItem.index, newIndexedItem.index, newIndexedItem.item, oldItemOffset, newItemOffset);
//                break;
//            }
//            case OP_DEL: {
//                int oldItemOffset = (oldIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) oldIndexedItem.item).off : oldIndexedItem.index);
//                result = String.format("{op:del, oldIdx:%d, oldOff:%d}", oldIndexedItem.index, oldItemOffset);
//                break;
//            }
//            case OP_MOVE: {
//                int oldItemOffset = (oldIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) oldIndexedItem.item).off : oldIndexedItem.index);
//                int newItemOffset = (newIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) newIndexedItem.item).off : newIndexedItem.index);
//                result = String.format("{op:move, oldIdx:%d, newIdx:%d, oldOff:%d, newOff:%d}", oldIndexedItem.index, newIndexedItem.index, oldItemOffset, newItemOffset);
//                break;
//            }
//            case OP_REPLACE: {
//                int oldItemOffset = (oldIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) oldIndexedItem.item).off : oldIndexedItem.index);
//                int newItemOffset = (newIndexedItem.item instanceof SectionItem ? ((SectionItem<T>) newIndexedItem.item).off : newIndexedItem.index);
//                result = String.format("{op:replace, oldIdx:%d, newIdx:%d, newVal:%s, oldOff:%d, newOff:%d}", oldIndexedItem.index, newIndexedItem.index, newIndexedItem.item, oldItemOffset, newItemOffset);
//                break;
//            }
//            default:
//        }
//        return result;
        return "";
    }
}
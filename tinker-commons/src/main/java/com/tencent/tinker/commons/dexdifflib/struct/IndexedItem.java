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

public class IndexedItem<T> implements Comparable<IndexedItem<T>> {
    public static final int INDEX_UNSET = -1;

    public final int index;
    public final T   item;

    public IndexedItem(int index, T item) {
        this.index = index;
        this.item = item;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(IndexedItem<T> o) {
        if (Comparable.class.isAssignableFrom(item.getClass()) && Comparable.class.isAssignableFrom(o.item.getClass())) {
            return ((Comparable<T>) item).compareTo(o.item);
        } else {
            throw new IllegalStateException("Tinker Exception:Invoke compareTo on unsupport item wrapped by IndexedItem.");
        }
    }
}

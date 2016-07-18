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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class PatchOpRecordList<T> implements List<PatchOpRecord<T>> {
    private final List<PatchOpRecord<T>> list;

    public PatchOpRecordList() {
        list = new ArrayList<>();
    }

    public PatchOpRecordList(PatchOpRecordList<T> o) {
        list = new ArrayList<>(o.list);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @Override
    public Iterator<PatchOpRecord<T>> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @SuppressWarnings("hiding")
    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean add(PatchOpRecord<T> e) {
        return list.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return list.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends PatchOpRecord<T>> c) {
        return list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends PatchOpRecord<T>> c) {
        return list.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return list.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return list.retainAll(c);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public PatchOpRecord<T> get(int index) {
        return list.get(index);
    }

    @Override
    public PatchOpRecord<T> set(int index, PatchOpRecord<T> element) {
        return list.set(index, element);
    }

    @Override
    public void add(int index, PatchOpRecord<T> element) {
        list.add(index, element);
    }

    @Override
    public PatchOpRecord<T> remove(int index) {
        return list.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<PatchOpRecord<T>> listIterator() {
        return list.listIterator();
    }

    @Override
    public ListIterator<PatchOpRecord<T>> listIterator(int index) {
        return list.listIterator(index);
    }

    @Override
    public List<PatchOpRecord<T>> subList(int fromIndex, int toIndex) {
        return list.subList(fromIndex, toIndex);
    }

    @Override
    public String toString() {
        return list.toString();
    }
}

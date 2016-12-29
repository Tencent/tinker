/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.tencent.tinker.android.utils;

/**
 * SparseBoolArrays map integers to booleans.  Unlike a normal array of booleans,
 * there can be gaps in the indices.  It is intended to be more memory efficient
 * than using a HashMap to map Integers to Booleans, both because it avoids
 * auto-boxing keys and values and its data structure doesn't rely on an extra entry object
 * for each mapping.
 *
 * <p>Note that this container keeps its mappings in an array data structure,
 * using a binary search to find keys.  The implementation is not intended to be appropriate for
 * data structures
 * that may contain large numbers of items.  It is generally slower than a traditional
 * HashMap, since lookups require a binary search and adds and removes require inserting
 * and deleting entries in the array.  For containers holding up to hundreds of items,
 * the performance difference is not significant, less than 50%.</p>
 *
 * <p>It is possible to iterate over the items in this container using
 * {@link #keyAt(int)} and {@link #valueAt(int)}. Iterating over the keys using
 * <code>keyAt(int)</code> with ascending values of the index will return the
 * keys in ascending order, or the values corresponding to the keys in ascending
 * order in the case of <code>valueAt(int)</code>.</p>
 */
public class SparseBoolArray implements Cloneable {
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final boolean[] EMPTY_BOOL_ARRAY = new boolean[0];
    private int[] mKeys;
    private boolean[] mValues;
    private int   mSize;

    public static class KeyNotFoundException extends Exception {
        public KeyNotFoundException() {
            super();
        }

        public KeyNotFoundException(String msg) {
            super(msg);
        }
    }

    /**
     * Creates a new SparseIntArray containing no mappings.
     */
    public SparseBoolArray() {
        this(10);
    }

    /**
     * Creates a new SparseIntArray containing no mappings that will not
     * require any additional memory allocation to store the specified
     * number of mappings.  If you supply an initial capacity of 0, the
     * sparse array will be initialized with a light-weight representation
     * not requiring any additional array allocations.
     */
    public SparseBoolArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = SparseBoolArray.EMPTY_INT_ARRAY;
            mValues = SparseBoolArray.EMPTY_BOOL_ARRAY;
        } else {
            mKeys = new int[initialCapacity];
            mValues = new boolean[initialCapacity];
        }
        mSize = 0;
    }

    /**
     * Given the current size of an array, returns an ideal size to which the array should grow.
     * This is typically double the given size, but should not be relied upon to do so in the
     * future.
     */
    private static int growSize(int currentSize) {
        return currentSize <= 4 ? 8 : currentSize + (currentSize >> 1);
    }

    @Override
    public SparseBoolArray clone() {
        SparseBoolArray clone = null;
        try {
            clone = (SparseBoolArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    /**
     * Gets the int mapped from the specified key, or a {@code KeyNotFoundException} is thrown
     * if no such mapping has been made.
     */
    public boolean get(int key) throws KeyNotFoundException {
        int i = binarySearch(mKeys, mSize, key);

        if (i < 0) {
            throw new KeyNotFoundException("" + key);
        } else {
            return mValues[i];
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    public void delete(int key) {
        int i = binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            removeAt(i);
        }
    }

    /**
     * Removes the mapping at the given index.
     */
    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        --mSize;
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    public void put(int key, boolean value) {
        int i = binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;
            mKeys = insertElementIntoIntArray(mKeys, mSize, i, key);
            mValues = insertElementIntoBoolArray(mValues, mSize, i, value);
            ++mSize;
        }
    }

    /**
     * Returns the number of key-value mappings that this SparseIntArray
     * currently stores.
     */
    public int size() {
        return mSize;
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the key from the <code>index</code>th key-value mapping that this
     * SparseIntArray stores.
     *
     * <p>The keys corresponding to indices in ascending order are guaranteed to
     * be in ascending order, e.g., <code>keyAt(0)</code> will return the
     * smallest key and <code>keyAt(size()-1)</code> will return the largest
     * key.</p>
     */
    public int keyAt(int index) {
        return mKeys[index];
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns
     * the value from the <code>index</code>th key-value mapping that this
     * SparseIntArray stores.
     *
     * <p>The values corresponding to indices in ascending order are guaranteed
     * to be associated with keys in ascending order, e.g.,
     * <code>valueAt(0)</code> will return the value associated with the
     * smallest key and <code>valueAt(size()-1)</code> will return the value
     * associated with the largest key.</p>
     */
    public boolean valueAt(int index) {
        return mValues[index];
    }

    /**
     * Returns the index for which {@link #keyAt} would return the
     * specified key, or a negative number if the specified
     * key is not mapped.
     */
    public int indexOfKey(int key) {
        return binarySearch(mKeys, mSize, key);
    }

    /**
     * Returns whether the {@code key} is exists.
     */
    public boolean containsKey(int key) {
        return indexOfKey(key) >= 0;
    }

    /**
     * Returns an index for which {@link #valueAt} would return the
     * specified key, or a negative number if no keys map to the
     * specified value.
     * Beware that this is a linear search, unlike lookups by key,
     * and that multiple keys can map to the same value and this will
     * find only one of them.
     */
    public int indexOfValue(boolean value) {
        for (int i = 0; i < mSize; ++i) {
            if (mValues[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes all key-value mappings from this SparseIntArray.
     */
    public void clear() {
        mSize = 0;
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where
     * the key is greater than all existing keys in the array.
     */
    public void append(int key, boolean value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = appendElementIntoIntArray(mKeys, mSize, key);
        mValues = appendElementIntoBoolArray(mValues, mSize, value);
        mSize++;
    }

    private int binarySearch(int[] array, int size, int value) {
        int lo = 0;
        int hi = size - 1;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int midVal = array[mid];

            if (midVal < value) {
                lo = mid + 1;
            } else if (midVal > value) {
                hi = mid - 1;
            } else {
                return mid;  // value found
            }
        }
        return ~lo;  // value not present
    }

    private int[] appendElementIntoIntArray(int[] array, int currentSize, int element) {
        if (currentSize > array.length) {
            throw new IllegalArgumentException("Bad currentSize, originalSize: " + array.length + " currentSize: " + currentSize);
        }
        if (currentSize + 1 > array.length) {
            int[] newArray = new int[SparseBoolArray.growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    private boolean[] appendElementIntoBoolArray(boolean[] array, int currentSize, boolean element) {
        if (currentSize > array.length) {
            throw new IllegalArgumentException("Bad currentSize, originalSize: " + array.length + " currentSize: " + currentSize);
        }
        if (currentSize + 1 > array.length) {
            boolean[] newArray = new boolean[SparseBoolArray.growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    private int[] insertElementIntoIntArray(int[] array, int currentSize, int index, int element) {
        if (currentSize > array.length) {
            throw new IllegalArgumentException("Bad currentSize, originalSize: " + array.length + " currentSize: " + currentSize);
        }

        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }

        int[] newArray = new int[SparseBoolArray.growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    private boolean[] insertElementIntoBoolArray(boolean[] array, int currentSize, int index, boolean element) {
        if (currentSize > array.length) {
            throw new IllegalArgumentException("Bad currentSize, originalSize: " + array.length + " currentSize: " + currentSize);
        }

        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }

        boolean[] newArray = new boolean[SparseBoolArray.growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation composes a string by iterating over its mappings.
     */
    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            int key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            boolean value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }
}

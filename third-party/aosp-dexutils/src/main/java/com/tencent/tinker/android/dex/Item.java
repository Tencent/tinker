package com.tencent.tinker.android.dex;

/**
 * Abstract item definition for holding elements of dex file
 * in a List.
 */
public abstract class Item<T> implements Comparable<T> {
    public abstract int getByteCountInDex();
}

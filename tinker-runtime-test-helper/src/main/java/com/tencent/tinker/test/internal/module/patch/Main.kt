package com.tencent.tinker.test.internal.module.patch

import android.os.Parcel
import android.os.Parcelable
import java.io.File

class ParcelableRawPatch(
    val version: String,
    val directory: File,
): Parcelable {

    constructor(parcel: Parcel): this(
        parcel.readString() ?: throw RuntimeException("\"version\" is null"),
        parcel.readString()?.let(::File) ?: throw RuntimeException("\"directory\" is null")
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(version)
        parcel.writeString(directory.absolutePath)
    }

    companion object CREATOR: Parcelable.Creator<ParcelableRawPatch> {
        override fun createFromParcel(parcel: Parcel): ParcelableRawPatch {
            return ParcelableRawPatch(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableRawPatch?> {
            return arrayOfNulls(size)
        }
    }
}
package com.tencent.tinker.lib.filepatch;

import android.content.Context;
import android.util.Log;

import com.tencent.tinker.lib.tinker.Tinker;

public class FilePatchFactory {
    private static final String TAG = "MicroMsg.FilePatchFactory";


    public static AbstractFilePatch getFilePatcher(Context context, boolean useCustomPatcher) {
        if (Tinker.with(context).getCustomPatcher() == null || !useCustomPatcher) {
            Log.i(TAG, "BsFilePatch");
            return new BsFilePatch();
        }
        Log.i(TAG, "CustomPatcher");
        return Tinker.with(context).getCustomPatcher();
    }

}

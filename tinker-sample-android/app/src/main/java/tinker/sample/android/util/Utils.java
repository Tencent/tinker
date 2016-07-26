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

package tinker.sample.android.util;

import android.os.Environment;
import android.os.StatFs;

import com.tencent.tinker.loader.shareutil.ShareConstants;

import java.io.File;

/**
 * Created by shwenzhang on 16/4/7.
 */
public class Utils {

    /**
     * the error code define by myself
     * should after {@code ShareConstants.ERROR_PATCH_INSERVICE
     */
    public static final int ERROR_PATCH_GOOGLEPLAY_CHANNEL = -5;
    public static final int ERROR_PATCH_ROM_SPACE          = -6;
    public static final int ERROR_PATCH_MEMORY_LIMIT       = -7;
    public static final int ERROR_PATCH_ALREADY_APPLY      = -8;
    public static final int ERROR_PATCH_CRASH_LIMIT        = -9;
    public static final int ERROR_PATCH_NOT_SUPPORT        = -10;

    public static final int MIN_MEMORY_HEAP_SIZE           = 45;

    private static boolean background = false;

    public static boolean isGooglePlay() {
        return false;
    }

    public static boolean isBackground() {
        return background;
    }

    public static void setBackground(boolean back) {
        background = back;
    }

    public static int checkForPatchRecover(long roomSize, int maxMemory) {
        if (Utils.isGooglePlay()) {
            return Utils.ERROR_PATCH_GOOGLEPLAY_CHANNEL;
        }
        if (maxMemory < MIN_MEMORY_HEAP_SIZE) {
            return Utils.ERROR_PATCH_MEMORY_LIMIT;
        }
        //or you can mention user to clean their rom space!
        if (!checkRomSpaceEnough(roomSize)) {
            return Utils.ERROR_PATCH_ROM_SPACE;
        }

        return ShareConstants.ERROR_PATCH_OK;
    }

    public static boolean isXposedExists(Throwable thr) {
        StackTraceElement[] stackTraces = thr.getStackTrace();
        for (StackTraceElement stackTrace : stackTraces) {
            final String clazzName = stackTrace.getClassName();
            if (clazzName != null && clazzName.contains("de.robv.android.xposed.XposedBridge")) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public static boolean checkRomSpaceEnough(long limitSize) {
        long allSize;
        long availableSize = 0;
        try {
            File data = Environment.getDataDirectory();
            StatFs sf = new StatFs(data.getPath());
            availableSize = (long) sf.getAvailableBlocks() * (long) sf.getBlockSize();
            allSize = (long) sf.getBlockCount() * (long) sf.getBlockSize();
        } catch (Exception e) {
            allSize = 0;
        }

        if (allSize != 0 && availableSize > limitSize) {
            return true;
        }
        return false;
    }
}

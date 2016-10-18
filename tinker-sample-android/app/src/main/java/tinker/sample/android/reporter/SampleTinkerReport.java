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

package tinker.sample.android.reporter;

import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import tinker.sample.android.util.Utils;

/**
 * a simple tinker data reporter
 * Created by zhangshaowen on 16/9/17.
 */
public class SampleTinkerReport {
    private static final String TAG = "Tinker.SampleTinkerReport";

    // KEY - PV
    public static final int KEY_REQUEST                   = 0;
    public static final int KEY_DOWNLOAD                  = 1;
    public static final int KEY_TRY_APPLY                 = 2;
    public static final int KEY_TRY_APPLY_SUCCESS         = 3;
    public static final int KEY_APPLIED_START             = 4;
    public static final int KEY_APPLIED                   = 5;
    public static final int KEY_LOADED                    = 6;
    public static final int KEY_CRASH_FAST_PROTECT        = 7;
    public static final int KEY_CRASH_CAUSE_XPOSED_DALVIK = 8;
    public static final int KEY_CRASH_CAUSE_XPOSED_ART    = 9;
    public static final int KEY_APPLY_WITH_RETRY          = 10;

    //Key -- try apply detail
    public static final int KEY_TRY_APPLY_REPAIR                  = 70;
    public static final int KEY_TRY_APPLY_UPGRADE                 = 71;
    public static final int KEY_TRY_APPLY_DISABLE                 = 72;
    public static final int KEY_TRY_APPLY_RUNNING                 = 73;
    public static final int KEY_TRY_APPLY_INSERVICE               = 74;
    public static final int KEY_TRY_APPLY_NOT_EXIST               = 75;
    public static final int KEY_TRY_APPLY_GOOGLEPLAY              = 76;
    public static final int KEY_TRY_APPLY_ROM_SPACE               = 77;
    public static final int KEY_TRY_APPLY_ALREADY_APPLY           = 78;
    public static final int KEY_TRY_APPLY_MEMORY_LIMIT            = 79;
    public static final int KEY_TRY_APPLY_CRASH_LIMIT             = 80;
    public static final int KEY_TRY_APPLY_CONDITION_NOT_SATISFIED = 81;

    //Key -- apply detail
    public static final int KEY_APPLIED_REPAIR       = 100;
    public static final int KEY_APPLIED_UPGRADE      = 101;
    public static final int KEY_APPLIED_REPAIR_FAIL  = 102;
    public static final int KEY_APPLIED_UPGRADE_FAIL = 103;

    public static final int KEY_APPLIED_EXCEPTION                               = 120;
    public static final int KEY_APPLIED_DEXOPT                                  = 121;
    public static final int KEY_APPLIED_INFO_CORRUPTED                          = 122;
    //package check
    public static final int KEY_APPLIED_PACKAGE_CHECK_SIGNATURE                 = 150;
    public static final int KEY_APPLIED_PACKAGE_CHECK_DEX_META                  = 151;
    public static final int KEY_APPLIED_PACKAGE_CHECK_LIB_META                  = 152;
    public static final int KEY_APPLIED_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND   = 153;
    public static final int KEY_APPLIED_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND = 154;
    public static final int KEY_APPLIED_PACKAGE_CHECK_META_NOT_FOUND            = 155;
    public static final int KEY_APPLIED_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL       = 156;
    public static final int KEY_APPLIED_PACKAGE_CHECK_RES_META                  = 157;
    public static final int KEY_APPLIED_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT    = 158;

    //version check
    public static final int KEY_APPLIED_VERSION_CHECK                           = 180;
    //extract error
    public static final int KEY_APPLIED_PATCH_FILE_EXTRACT                      = 181;
    public static final int KEY_APPLIED_DEX_EXTRACT                             = 182;
    /**
     * for art small dex
     */
    public static final int KEY_APPLIED_DEX_ART_EXTRACT                         = 183;
    public static final int KEY_APPLIED_LIB_EXTRACT                             = 184;
    public static final int KEY_APPLIED_RESOURCE_EXTRACT                        = 185;
    //cost time
    public static final int KEY_APPLIED_SUCC_COST_5S_LESS                       = 200;
    public static final int KEY_APPLIED_SUCC_COST_10S_LESS                      = 201;
    public static final int KEY_APPLIED_SUCC_COST_30S_LESS                      = 202;
    public static final int KEY_APPLIED_SUCC_COST_60S_LESS                      = 203;
    public static final int KEY_APPLIED_SUCC_COST_OTHER                         = 204;

    public static final int KEY_APPLIED_FAIL_COST_5S_LESS  = 205;
    public static final int KEY_APPLIED_FAIL_COST_10S_LESS = 206;
    public static final int KEY_APPLIED_FAIL_COST_30S_LESS = 207;
    public static final int KEY_APPLIED_FAIL_COST_60S_LESS = 208;
    public static final int KEY_APPLIED_FAIL_COST_OTHER    = 209;


    // KEY -- load detail
    public static final int KEY_LOADED_UNKNOWN_EXCEPTION        = 250;
    public static final int KEY_LOADED_UNCAUGHT_EXCEPTION       = 251;
    public static final int KEY_LOADED_EXCEPTION_DEX            = 252;
    public static final int KEY_LOADED_EXCEPTION_DEX_CHECK      = 253;
    public static final int KEY_LOADED_EXCEPTION_RESOURCE       = 254;
    public static final int KEY_LOADED_EXCEPTION_RESOURCE_CEHCK = 255;


    public static final int KEY_LOADED_MISMATCH_DEX       = 300;
    public static final int KEY_LOADED_MISMATCH_LIB       = 301;
    public static final int KEY_LOADED_MISMATCH_RESOURCE  = 302;
    public static final int KEY_LOADED_MISSING_DEX        = 303;
    public static final int KEY_LOADED_MISSING_LIB        = 304;
    public static final int KEY_LOADED_MISSING_PATCH_FILE = 305;
    public static final int KEY_LOADED_MISSING_PATCH_INFO = 306;
    public static final int KEY_LOADED_MISSING_DEX_OPT    = 307;
    public static final int KEY_LOADED_MISSING_RES        = 308;
    public static final int KEY_LOADED_INFO_CORRUPTED     = 309;

    //load package check
    public static final int KEY_LOADED_PACKAGE_CHECK_SIGNATURE                 = 350;
    public static final int KEY_LOADED_PACKAGE_CHECK_DEX_META                  = 351;
    public static final int KEY_LOADED_PACKAGE_CHECK_LIB_META                  = 352;
    public static final int KEY_LOADED_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND   = 353;
    public static final int KEY_LOADED_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND = 354;
    public static final int KEY_LOADED_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL       = 355;
    public static final int KEY_LOADED_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND    = 356;
    public static final int KEY_LOADED_PACKAGE_CHECK_RES_META                  = 357;
    public static final int KEY_LOADED_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT    = 358;


    public static final int KEY_LOADED_SUCC_COST_500_LESS  = 400;
    public static final int KEY_LOADED_SUCC_COST_1000_LESS = 401;
    public static final int KEY_LOADED_SUCC_COST_3000_LESS = 402;
    public static final int KEY_LOADED_SUCC_COST_5000_LESS = 403;
    public static final int KEY_LOADED_SUCC_COST_OTHER     = 404;

    interface Reporter {
        void onReport(int key);

        void onReport(String message);
    }

    private static Reporter reporter = null;

    public void setReporter(Reporter reporter) {
        this.reporter = reporter;
    }

    public static void onTryApply(boolean upgrade, boolean success) {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_TRY_APPLY);
        if (upgrade) {
            reporter.onReport(KEY_TRY_APPLY_UPGRADE);
        } else {
            reporter.onReport(KEY_TRY_APPLY_REPAIR);
        }
        if (success) {
            reporter.onReport(KEY_TRY_APPLY_SUCCESS);
        }
    }

    public static void onTryApplyFail(int errorCode) {
        if (reporter == null) {
            return;
        }
        switch (errorCode) {
            case ShareConstants.ERROR_PATCH_NOTEXIST:
                reporter.onReport(KEY_TRY_APPLY_NOT_EXIST);
                break;
            case ShareConstants.ERROR_PATCH_DISABLE:
                reporter.onReport(KEY_TRY_APPLY_DISABLE);
                break;
            case ShareConstants.ERROR_PATCH_INSERVICE:
                reporter.onReport(KEY_TRY_APPLY_INSERVICE);
                break;
            case ShareConstants.ERROR_PATCH_RUNNING:
                reporter.onReport(KEY_TRY_APPLY_RUNNING);
                break;
            case Utils.ERROR_PATCH_ROM_SPACE:
                reporter.onReport(KEY_TRY_APPLY_ROM_SPACE);
                break;
            case Utils.ERROR_PATCH_GOOGLEPLAY_CHANNEL:
                reporter.onReport(KEY_TRY_APPLY_GOOGLEPLAY);
                break;
            case Utils.ERROR_PATCH_ALREADY_APPLY:
                reporter.onReport(KEY_TRY_APPLY_ALREADY_APPLY);
                break;
            case Utils.ERROR_PATCH_CRASH_LIMIT:
                reporter.onReport(KEY_TRY_APPLY_CRASH_LIMIT);
                break;
            case Utils.ERROR_PATCH_MEMORY_LIMIT:
                reporter.onReport(KEY_TRY_APPLY_MEMORY_LIMIT);
                break;
            case Utils.ERROR_PATCH_CONDITION_NOT_SATISFIED:
                reporter.onReport(KEY_TRY_APPLY_CONDITION_NOT_SATISFIED);
                break;
        }
    }

    public static void onLoadPackageCheckFail(int errorCode) {
        if (reporter == null) {
            return;
        }
        switch (errorCode) {
            case ShareConstants.ERROR_PACKAGE_CHECK_SIGNATURE_FAIL:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_SIGNATURE);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_DEX_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_LIB_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL);

                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_RES_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT:
                reporter.onReport(KEY_LOADED_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT);
                break;
        }
    }

    public static void onLoaded(long cost) {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_LOADED);

        if (cost < 0L) {
            TinkerLog.e(TAG, "hp_report report load cost failed, invalid cost");
            return;
        }

        if (cost <= 500) {
            reporter.onReport(KEY_LOADED_SUCC_COST_500_LESS);
        } else if (cost <= 1000) {
            reporter.onReport(KEY_LOADED_SUCC_COST_1000_LESS);
        } else if (cost <= 3000) {
            reporter.onReport(KEY_LOADED_SUCC_COST_3000_LESS);
        } else if (cost <= 5000) {
            reporter.onReport(KEY_LOADED_SUCC_COST_5000_LESS);
        } else {
            reporter.onReport(KEY_LOADED_SUCC_COST_OTHER);
        }
    }

    public static void onLoadInfoCorrupted() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_LOADED_INFO_CORRUPTED);
    }

    public static void onLoadFileNotFound(int fileType) {
        if (reporter == null) {
            return;
        }
        switch (fileType) {
            case ShareConstants.TYPE_DEX_OPT:
                reporter.onReport(KEY_LOADED_MISSING_DEX_OPT);
                break;
            case ShareConstants.TYPE_DEX:
                reporter.onReport(KEY_LOADED_MISSING_DEX);
                break;
            case ShareConstants.TYPE_LIBRARY:
                reporter.onReport(KEY_LOADED_MISSING_LIB);
                break;
            case ShareConstants.TYPE_PATCH_FILE:
                reporter.onReport(KEY_LOADED_MISSING_PATCH_FILE);
                break;
            case ShareConstants.TYPE_PATCH_INFO:
                reporter.onReport(KEY_LOADED_MISSING_PATCH_INFO);
                break;
            case ShareConstants.TYPE_RESOURCE:
                reporter.onReport(KEY_LOADED_MISSING_RES);
                break;
        }
    }

    public static void onLoadFileMisMatch(int fileType) {
        if (reporter == null) {
            return;
        }
        switch (fileType) {
            case ShareConstants.TYPE_DEX:
                reporter.onReport(KEY_LOADED_MISMATCH_DEX);
                break;
            case ShareConstants.TYPE_LIBRARY:
                reporter.onReport(KEY_LOADED_MISMATCH_LIB);
                break;
            case ShareConstants.TYPE_RESOURCE:
                reporter.onReport(KEY_LOADED_MISMATCH_RESOURCE);
                break;
        }
    }

    public static void onLoadException(Throwable throwable, int errorCode) {
        if (reporter == null) {
            return;
        }
        boolean isCheckFail = false;
        switch (errorCode) {
            case ShareConstants.ERROR_LOAD_EXCEPTION_DEX:
                if (throwable.getMessage().contains(ShareConstants.CHECK_DEX_INSTALL_FAIL)) {
                    reporter.onReport(KEY_LOADED_EXCEPTION_DEX_CHECK);
                    isCheckFail = true;
                    TinkerLog.e(TAG, "tinker dex check fail:" + throwable.getMessage());
                } else {
                    reporter.onReport(KEY_LOADED_EXCEPTION_DEX);
                    TinkerLog.e(TAG, "tinker dex reflect fail:" + throwable.getMessage());
                }
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_RESOURCE:
                if (throwable.getMessage().contains(ShareConstants.CHECK_RES_INSTALL_FAIL)) {
                    reporter.onReport(KEY_LOADED_EXCEPTION_RESOURCE_CEHCK);
                    isCheckFail = true;
                    TinkerLog.e(TAG, "tinker res check fail:" + throwable.getMessage());
                } else {
                    reporter.onReport(KEY_LOADED_EXCEPTION_RESOURCE);
                    TinkerLog.e(TAG, "tinker res reflect fail:" + throwable.getMessage());
                }
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_UNCAUGHT:
                reporter.onReport(KEY_LOADED_UNCAUGHT_EXCEPTION);
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_UNKNOWN:
                reporter.onReport(KEY_LOADED_UNKNOWN_EXCEPTION);
                break;
        }
        //reporter exception, for dex check fail, we don't need to report stacktrace
        if (!isCheckFail) {
            reporter.onReport("Tinker Exception:load tinker occur exception " + Utils.getExceptionCauseString(throwable));
        }
    }

    public static void onApplyPatchServiceStart() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLIED_START);
    }

    public static void onApplyDexOptFail(Throwable throwable) {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLIED_DEXOPT);
        reporter.onReport("Tinker Exception:apply tinker occur exception " + Utils.getExceptionCauseString(throwable));
    }

    public static void onApplyInfoCorrupted() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLIED_INFO_CORRUPTED);
    }

    public static void onApplyVersionCheckFail() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLIED_VERSION_CHECK);
    }

    public static void onApplyExtractFail(int fileType) {
        if (reporter == null) {
            return;
        }
        switch (fileType) {
            case ShareConstants.TYPE_DEX:
                reporter.onReport(KEY_APPLIED_DEX_EXTRACT);
                break;
            case ShareConstants.TYPE_DEX_FOR_ART:
                reporter.onReport(KEY_APPLIED_DEX_ART_EXTRACT);
                break;
            case ShareConstants.TYPE_LIBRARY:
                reporter.onReport(KEY_APPLIED_LIB_EXTRACT);
                break;
            case ShareConstants.TYPE_PATCH_FILE:
                reporter.onReport(KEY_APPLIED_PATCH_FILE_EXTRACT);
                break;
            case ShareConstants.TYPE_RESOURCE:
                reporter.onReport(KEY_APPLIED_RESOURCE_EXTRACT);
                break;
        }
    }

    public static void onApplied(boolean isUpgrade, long cost, boolean success) {
        if (reporter == null) {
            return;
        }
        if (success) {
            reporter.onReport(KEY_APPLIED);
        }

        if (isUpgrade) {
            if (success) {
                reporter.onReport(KEY_APPLIED_UPGRADE);
            } else {
                reporter.onReport(KEY_APPLIED_UPGRADE_FAIL);
            }

        } else {
            if (success) {
                reporter.onReport(KEY_APPLIED_REPAIR);
            } else {
                reporter.onReport(KEY_APPLIED_REPAIR_FAIL);
            }
        }

        TinkerLog.i(TAG, "hp_report report apply cost = %d", cost);

        if (cost < 0L) {
            TinkerLog.e(TAG, "hp_report report apply cost failed, invalid cost");
            return;
        }

        if (cost <= 5000) {
            if (success) {
                reporter.onReport(KEY_APPLIED_SUCC_COST_5S_LESS);
            } else {
                reporter.onReport(KEY_APPLIED_FAIL_COST_5S_LESS);
            }
        } else if (cost <= 10 * 1000) {
            if (success) {
                reporter.onReport(KEY_APPLIED_SUCC_COST_10S_LESS);
            } else {
                reporter.onReport(KEY_APPLIED_FAIL_COST_10S_LESS);
            }
        } else if (cost <= 30 * 1000) {
            if (success) {
                reporter.onReport(KEY_APPLIED_SUCC_COST_30S_LESS);
            } else {
                reporter.onReport(KEY_APPLIED_FAIL_COST_30S_LESS);
            }
        } else if (cost <= 60 * 1000) {
            if (success) {
                reporter.onReport(KEY_APPLIED_SUCC_COST_60S_LESS);
            } else {
                reporter.onReport(KEY_APPLIED_FAIL_COST_60S_LESS);
            }
        } else {
            if (success) {
                reporter.onReport(KEY_APPLIED_SUCC_COST_OTHER);
            } else {
                reporter.onReport(KEY_APPLIED_FAIL_COST_OTHER);
            }
        }
    }

    public static void onApplyPackageCheckFail(int errorCode) {
        if (reporter == null) {
            return;
        }
        TinkerLog.i(TAG, "hp_report package check failed, error = %d", errorCode);

        switch (errorCode) {
            case ShareConstants.ERROR_PACKAGE_CHECK_SIGNATURE_FAIL:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_SIGNATURE);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_DEX_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_LIB_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_META_NOT_FOUND);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_RES_META);
                break;
            case ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT:
                reporter.onReport(KEY_APPLIED_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT);
                break;
        }
    }

    public static void onApplyCrash(Throwable throwable) {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLIED_EXCEPTION);
        reporter.onReport("Tinker Exception:apply tinker occur exception " + Utils.getExceptionCauseString(throwable));
    }

    public static void onFastCrashProtect() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_CRASH_FAST_PROTECT);
    }

    public static void onXposedCrash() {
        if (reporter == null) {
            return;
        }
        if (ShareTinkerInternals.isVmArt()) {
            reporter.onReport(KEY_CRASH_CAUSE_XPOSED_ART);
        } else {
            reporter.onReport(KEY_CRASH_CAUSE_XPOSED_DALVIK);
        }
    }

    public static void onReportRetryPatch() {
        if (reporter == null) {
            return;
        }
        reporter.onReport(KEY_APPLY_WITH_RETRY);
    }

}

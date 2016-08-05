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

package com.tencent.tinker.loader;

import android.content.Intent;

import com.tencent.tinker.loader.shareutil.ShareBsDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by zhangshaowen on 16/3/8.
 */

/**
 * check the complete of the dex files
 * pre-load patch dex files
 * library的patch文件放于lib文件夹，后面以他本身来安装包的位置保持一直
 * 这里我们只对lib下面的库做托管，对于assets下面的库，交由开发者自己实现
 */
public class TinkerSoLoader {

    protected static final String SO_MEAT_FILE = ShareConstants.SO_META_FILE;
    protected static final String SO_PATH      = ShareConstants.SO_PATH;
    private static final String TAG = "TinkerSoLoader";

    /**
     * all the library files in meta file exist?
     * fast check, only check whether exist
     *
     * @param directory
     * @return boolean
     */
    public static boolean checkComplete(String directory, ShareSecurityCheck securityCheck, Intent intentResult) {
        String meta = securityCheck.getMetaContentMap().get(SO_MEAT_FILE);
        //not found lib
        if (meta == null) {
            return true;
        }
        ArrayList<ShareBsDiffPatchInfo> libraryList = new ArrayList<>();
        ShareBsDiffPatchInfo.parseDiffPatchInfo(meta, libraryList);

        if (libraryList.isEmpty()) {
            return true;
        }

        //tinker//patch-641e634c/lib
        String libraryPath = directory + "/" + SO_PATH + "/";

        HashMap<String, String> libs = new HashMap<>();

        for (ShareBsDiffPatchInfo info : libraryList) {
            if (!ShareBsDiffPatchInfo.checkDiffPatchInfo(info)) {
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
                return false;
            }
            String middle = info.path + "/" + info.name;

            //unlike dex, keep the original structure
            libs.put(middle, info.md5);
        }

        File libraryDir = new File(libraryPath);

        if (!libraryDir.exists() || !libraryDir.isDirectory()) {
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_DIRECTORY_NOT_EXIST);
            return false;
        }

        //fast check whether there is any dex files missing
        for (String relative : libs.keySet()) {
            File libFile = new File(libraryPath + relative);
            if (!libFile.exists()) {
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_FILE_NOT_EXIST);
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISSING_LIB_PATH, libFile.getAbsolutePath());
                return false;
            }
        }

        //if is ok, add to result intent
        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_LIBS_PATH, libs);
        return true;
    }


}
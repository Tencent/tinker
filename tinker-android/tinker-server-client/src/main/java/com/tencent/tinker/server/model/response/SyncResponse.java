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

package com.tencent.tinker.server.model.response;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by sun on 10/10/2016.
 */

public final class SyncResponse {

    private static final String KEY_VERSION = "v";
    private static final String KEY_GRAY = "g";
    private static final String KEY_CONDITIONS = "c";
    private static final String KEY_PAUSE = "p";
    public final String version;
    public final Integer grayValue;
    public final String conditions;
    public final Boolean isPaused;

    private SyncResponse(String version, Integer grayValue, String conditions, Boolean pause) {
        this.version = version;
        this.conditions = conditions;
        this.isPaused = pause;
        if (grayValue == 0) {
            this.grayValue = null;
        } else {
            this.grayValue = grayValue;
        }
    }

    public static SyncResponse fromJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            String version = jsonObject.getString(KEY_VERSION);
            String conditions = jsonObject.optString(KEY_CONDITIONS);
            Integer grayValue = jsonObject.optInt(KEY_GRAY);
            Integer pauseFlag = jsonObject.optInt(KEY_PAUSE);
            return new SyncResponse(version, grayValue, conditions, pauseFlag == 1);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String toString() {
        return "version:" + version + "\ngrayValue:" + grayValue + "\nconditions:" + conditions
            + "\npause:" + isPaused;
    }
}

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

package com.tencent.tinker.server.model.request;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by sun on 24/10/2016.
 */
public class BaseReport {
    public final String appKey;
    public final String appVersion;
    public final String patchVersion;
    public final Integer platformType;

    public BaseReport(String appKey, String appVersion, String patchVersion) {
        this.appKey = appKey;
        this.appVersion = appVersion;
        this.patchVersion = patchVersion;
        this.platformType = 1;
    }

    protected JSONObject toJsonObject() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("k", appKey);
        jsonObject.put("av", appVersion);
        jsonObject.put("pv", patchVersion);
        jsonObject.put("t", platformType);
        return jsonObject;
    }

    public String toJson() {
        try {
            return toJsonObject().toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }
}

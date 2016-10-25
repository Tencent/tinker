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

package com.tencent.tinker.server;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.server.model.DataFetcher;
import com.tencent.tinker.server.model.TinkerClientUrl;
import com.tencent.tinker.server.model.request.FailReport;
import com.tencent.tinker.server.model.request.SuccessReport;
import com.tencent.tinker.server.model.response.SyncResponse;
import com.tencent.tinker.server.urlconnection.UrlConnectionUrlLoader;
import com.tencent.tinker.server.utils.Conditions;
import com.tencent.tinker.server.utils.Installation;
import com.tencent.tinker.server.utils.Preconditions;
import com.tencent.tinker.server.utils.Utils;
import com.tencent.tinker.server.utils.VersionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class TinkerClientImp implements TinkerClientAPI {

    public static final String TAG = "Tinker.ClientImp";

    private static volatile TinkerClientImp        client;

    private final String                 appVersion;
    private final String                 appKey;
    private final String                 host;
    private final boolean                debug;
    private final Conditions             conditions;
    private       UrlConnectionUrlLoader loader;

    TinkerClientImp(String appKey, String appVersion, String host, Boolean debug, Conditions conditions) {
        this.appVersion = appVersion;
        this.appKey = appKey;
        this.host = host;
        this.debug = debug;
        this.conditions = conditions;
    }

    /**
     * Singleton get method for Tinker client, you need invoke
     * {@link #init(Context, String, String, Boolean)} before it invoke.
     *
     * @return the instance of {@link TinkerClientImp}
     */
    public static TinkerClientImp get() {
        if (client == null) {
            throw new RuntimeException("Please invoke init Tinker Client first");
        }
        return client;
    }

    /**
     * init the Tinker Client, it only effect at first time.
     * you should only invoke once in your app lifecycle
     *
     * @param context    {@link Context} context
     * @param appKey     your appKey get from <a href=tinkerpatch.com>tinkerpatch.com<a/>
     * @param appVersion your app version, this is "App版本号" in tinkerpatch.com
     * @param debug      use debug config, which is "开发预览" in tinkerpatch.com
     * @return {@link #TinkerClientImp} tinker patch client
     */
    public static TinkerClientImp init(Context context, String appKey, String appVersion, Boolean debug) {
        if (client == null) {
            synchronized (TinkerClientImp.class) {
                if (client == null) {
                    client = new Builder()
                        .appKey(appKey)
                        .appVersion(appVersion)
                        .debug(debug)
                        .conditions(context)
                        .build();

                    client.loader = new UrlConnectionUrlLoader();
                }
            }
        }
        return client;
    }

    public TinkerClientImp params(String key, String value) {
        this.conditions.set(key, value);
        return this;
    }

    public void save(Context context) {
        try {
            this.conditions.save(context);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update(final Context context, final DataFetcher.DataCallback<? super File> callback) {

        if (callback == null) {
            throw new RuntimeException("callback can't be null");
        }

        sync(context, new DataFetcher.DataCallback<String>() {
            @Override
            public void onDataReady(String data) {
                SyncResponse response = SyncResponse.fromJson(data);
                if (response == null) {
                    callback.onLoadFailed(new RuntimeException("Can't sync with version: response == null"));
                } else {

                    DataFetcher.DataCallback<File> downloadCallback = new DataFetcher.DataCallback<File>() {
                        @Override
                        public void onDataReady(File data) {
                            callback.onDataReady(data);
                        }

                        @Override
                        public void onLoadFailed(Exception e) {
                            callback.onLoadFailed(e);
                        }
                    };

                    if (isUpdate(context, response.version)
                        && !response.isPaused
                        && Utils.isInGrayGroup(response.grayValue, context)
                        && conditions.check(response.conditions)) {

                        String patchPath = getPatchFilePath(context, response.version);
                        download(context, response.version, patchPath, downloadCallback);
                    } else {
                        TinkerLog.i(TAG, "Needn't update, sync response is: " + response.toString()
                            + "\ngray: " + Installation.grayValue(context));
                    }
                }
            }

            @Override
            public void onLoadFailed(Exception e) {
                callback.onLoadFailed(e);
            }
        });
    }

    @Override
    public void sync(final Context context, final DataFetcher.DataCallback<String> callback) {
        Uri.Builder urlBuilder = Uri.parse(this.host).buildUpon();
        if (client.debug) {
            urlBuilder.appendPath("dev");
        }
        final String url = urlBuilder.appendPath(this.appKey)
            .appendPath(this.appVersion)
            .appendQueryParameter("d", Installation.id(context))
            .appendQueryParameter("v", String.valueOf(System.currentTimeMillis()))
            .build().toString();

        TinkerClientUrl tkClientUrl = new TinkerClientUrl.Builder().url(url).build();

        final DataFetcher<InputStream> dataFetcher = loader.buildLoadData(tkClientUrl);
        dataFetcher.loadData(new DataFetcher.DataCallback<InputStream>() {
            @Override
            public void onDataReady(InputStream data) {
                if (callback == null) {
                    return;
                }
                try {
                    String response = Utils.readStreamToString(data, Utils.CHARSET);
                    SyncResponse.fromJson(response);
                    callback.onDataReady(response);
                } catch (Exception e) {
                    callback.onLoadFailed(e);
                } finally {
                    dataFetcher.cleanup();
                }
            }

            @Override
            public void onLoadFailed(Exception e) {
                if (callback == null) {
                    return;
                }
                try {
                    callback.onLoadFailed(e);
                } finally {
                    dataFetcher.cleanup();
                }
            }
        });
    }

    @Override
    public void download(final Context context,
                         final String patchVersion,
                         final String filePath,
                         final DataFetcher.DataCallback<? super File> callback) {

        Preconditions.checkNotEmpty(patchVersion);
        final String url = Uri.parse(this.host)
            .buildUpon()
            .appendPath(this.appKey)
            .appendPath(this.appVersion)
            .appendPath(String.format("file%s", patchVersion))
            .appendQueryParameter("d", Installation.id(context))
            .appendQueryParameter("v", String.valueOf(System.currentTimeMillis()))
            .build().toString();

        TinkerClientUrl tkClientUrl = new TinkerClientUrl.Builder().url(url).build();

        final DataFetcher<InputStream> dataFetcher = loader.buildLoadData(tkClientUrl);
        dataFetcher.loadData(new DataFetcher.DataCallback<InputStream>() {
            @Override
            public void onDataReady(InputStream data) {
                if (callback == null) {
                    return;
                }
                try {
                    callback.onDataReady(Utils.readStreamToFile(data, filePath));
                    VersionUtils.update(context, Integer.parseInt(patchVersion), filePath);
                } catch (Exception e) {
                    callback.onLoadFailed(e);
                } finally {
                    dataFetcher.cleanup();
                }
            }

            @Override
            public void onLoadFailed(Exception e) {
                if (callback == null) {
                    return;
                }

                try {
                    callback.onLoadFailed(e);
                } finally {
                    dataFetcher.cleanup();
                }
            }
        });
    }

    @Override
    public void reportSuccess(Context context, String patchVersion) {
        Uri.Builder urlBuilder = Uri.parse(TinkerClientAPI.REPORT_SUCCESS_URL).buildUpon();
        final String url = urlBuilder.build().toString();
        SuccessReport report = new SuccessReport(this.appKey, this.appVersion, patchVersion);
        TinkerClientUrl tkClientUrl = new TinkerClientUrl.Builder()
            .url(url)
            .body(report.toJson())
            .method("POST").build();
        final DataFetcher<InputStream> dataFetcher = loader.buildLoadData(tkClientUrl);
        dataFetcher.loadData(new DataFetcher.DataCallback<InputStream>() {
            @Override
            public void onDataReady(InputStream data) {
                TinkerLog.d(TAG, "reportSuccess successfully");
            }

            @Override
            public void onLoadFailed(Exception e) {
                TinkerLog.e(TAG, "reportSuccess error", e);
            }
        });
    }

    @Override
    public void reportFail(Context context, String patchVersion, Integer errCode) {
        Uri.Builder urlBuilder = Uri.parse(TinkerClientAPI.REPORT_FAIL_URL).buildUpon();
        final String url = urlBuilder.build().toString();
        FailReport report = new FailReport(this.appKey, this.appVersion, patchVersion, errCode);
        TinkerClientUrl tkClientUrl = new TinkerClientUrl.Builder()
            .url(url)
            .body(report.toJson())
            .method("POST").build();
        final DataFetcher<InputStream> dataFetcher = loader.buildLoadData(tkClientUrl);
        dataFetcher.loadData(new DataFetcher.DataCallback<InputStream>() {
            @Override
            public void onDataReady(InputStream data) {
                TinkerLog.d(TAG, "reportFail successfully");
            }

            @Override
            public void onLoadFailed(Exception e) {
                TinkerLog.e(TAG, "reportSuccess error", e);
            }
        });
    }

    /**
     * Get current patch version
     *
     * @param context {@link Context}
     * @return patch version
     */
    public Integer getCurrentPatchVersoin(Context context) {
        return VersionUtils.getCurrentVersion(context);
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getHost() {
        return host;
    }

    public boolean isDebug() {
        return debug;
    }

    public Conditions getConditions() {
        return conditions;
    }

    String getPatchFilePath(Context context, String version) {
        File file = new File(context.getFilesDir(), Utils.DEFAULT_PATCH_PATH_PREFIX + version);
        return file.getAbsolutePath();
    }

    Boolean isUpdate(Context context, String version) {
        Integer latestVersion = Integer.parseInt(version);
        Integer currentVersion = VersionUtils.getCurrentVersion(context);
        return latestVersion > currentVersion;
    }

    static class Builder {
        private static final String HOST_URL = "http://q.tinkerpatch.com";
        private String     appVersion;
        private String     appKey;
        private String     host;
        private Boolean    debug;
        private Conditions conditions;

        TinkerClientImp.Builder host(String host) {
            this.host = host;
            return this;
        }

        TinkerClientImp.Builder appKey(String appKey) {
            this.appKey = appKey;
            return this;
        }

        TinkerClientImp.Builder appVersion(String appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        TinkerClientImp.Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        TinkerClientImp.Builder conditions(Context context) {
            this.conditions = new Conditions(context);
            return this;
        }

        void makeDefault() {
            if (TextUtils.isEmpty(host)) {
                this.host = HOST_URL;
            }
            if (TextUtils.isEmpty(this.appKey) || TextUtils.isEmpty(this.appVersion)) {
                throw new RuntimeException("You need setup Appkey and AppVersion");
            }
            if (this.conditions == null) {
                throw new RuntimeException("You need init conditions property");
            }
        }

        public TinkerClientImp build() {
            makeDefault();
            return new TinkerClientImp(this.appKey, this.appVersion, this.host, this.debug, this.conditions);
        }
    }
}

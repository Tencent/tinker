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

package com.tencent.tinker.server.urlconnection;



import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.server.model.DataFetcher;
import com.tencent.tinker.server.model.TinkerClientUrl;
import com.tencent.tinker.server.utils.Preconditions;
import com.tencent.tinker.server.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.Executor;


public class UrlConnectionStreamFetcher implements DataFetcher<InputStream> {

    private static final String TAG = "UrlConnectionFetcher";
    private final TinkerClientUrl tkUrl;
    private final Executor        executor;
    InputStream stream;

    public UrlConnectionStreamFetcher(Executor executor, TinkerClientUrl tkUrl) {
        this.tkUrl = tkUrl;
        this.executor = executor;
    }

    @Override
    public void loadData(final DataCallback<? super InputStream> callback) {
        ConnectionWorker worker = new ConnectionWorker(tkUrl, new DataCallback<InputStream>() {
            @Override
            public void onDataReady(InputStream data) {
                stream = data;
                callback.onDataReady(data);
            }

            @Override
            public void onLoadFailed(Exception e) {
                callback.onLoadFailed(e);
            }
        });
        executor.execute(worker);
    }

    @Override
    public void cleanup() {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cancel() {
        // NOT IMPLEMENT
    }

    @Override
    public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    private static class ConnectionWorker implements Runnable {

        private final DataCallback<? super InputStream> callback;
        private final TinkerClientUrl                   url;

        ConnectionWorker(TinkerClientUrl url, DataCallback<? super InputStream> callback) {
            this.callback = Preconditions.checkNotNull(callback);
            this.url = Preconditions.checkNotNull(url);
        }

        @Override
        public void run() {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
                conn.setRequestMethod(url.getMethod());
                conn.setDoOutput(true);
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(false);
                for (Map.Entry<String, String> entry : url.getHeaders().entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
                switch (url.getMethod()) {
                    case "GET":
                        break;
                    case "POST":
                        OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), Utils.CHARSET);
                        writer.write(url.getBody());
                        writer.flush();
                        writer.close();
                        break;
                    default:
                        throw new RuntimeException("Unsupported request method" + url.getMethod());
                }
                conn.connect();
                TinkerLog.d(TAG, "response code " + conn.getResponseCode() + " msg: " + conn.getResponseMessage());
                InputStream inputStream = conn.getInputStream();
                this.callback.onDataReady(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
                this.callback.onLoadFailed(e);
            }
        }
    }
}

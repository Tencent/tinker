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

/**
 * Created by shwenzhang on 16/7/8.
 */
public class TinkerRuntimeException extends RuntimeException {
    private static final String TINKER_RUNTIME_EXCEPTION_PREFIX = "Tinker Exception:";
    private static final long serialVersionUID = 1L;

    public TinkerRuntimeException(String detailMessage) {
        super(TINKER_RUNTIME_EXCEPTION_PREFIX + detailMessage);
    }

    public TinkerRuntimeException(String detailMessage, Throwable throwable) {
        super(TINKER_RUNTIME_EXCEPTION_PREFIX + detailMessage, throwable);
    }

}

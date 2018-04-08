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

package com.tencent.tinker.ziputils.ziputil;

/**
 * modify by zhangshaowen on 16/6/7.
 *
 * Do not add constants to this interface! It's implemented by the classes
 * in this package whose names start "Zip", and the constants are thereby
 * public API.
 */
interface ZipConstants {
    long LOCSIG = 0x4034b50, EXTSIG = 0x8074b50,
            CENSIG = 0x2014b50, ENDSIG = 0x6054b50;
    int LOCHDR = 30, EXTHDR = 16, CENHDR = 46, ENDHDR = 22,
            LOCVER = 4, LOCFLG = 6, LOCHOW = 8, LOCTIM = 10, LOCCRC = 14,
            LOCSIZ = 18, LOCLEN = 22, LOCNAM = 26, LOCEXT = 28, EXTCRC = 4,
            EXTSIZ = 8, EXTLEN = 12, CENVEM = 4, CENVER = 6, CENFLG = 8,
            CENHOW = 10, CENTIM = 12, CENCRC = 16, CENSIZ = 20, CENLEN = 24,
            CENNAM = 28, CENEXT = 30, CENCOM = 32, CENDSK = 34, CENATT = 36,
            CENATX = 38, CENOFF = 42, ENDSUB = 8, ENDTOT = 10, ENDSIZ = 12,
            ENDOFF = 16, ENDCOM = 20;
}

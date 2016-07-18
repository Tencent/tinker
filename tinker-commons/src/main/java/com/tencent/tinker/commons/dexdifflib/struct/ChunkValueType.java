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

package com.tencent.tinker.commons.dexdifflib.struct;

/**
 * Created by tomystang on 2016/4/1.
 */
public class ChunkValueType {

    public static final byte TYPE_BYTE                   = 0x01;
    public static final byte TYPE_SHORT                  = 0x02;
    public static final byte TYPE_INT                    = 0x03;
    public static final byte TYPE_STRINGDATA             = 0x04;
    public static final byte TYPE_TYPEID                 = 0x05;
    public static final byte TYPE_TYPELIST               = 0x06;
    public static final byte TYPE_PROTOID                = 0x07;
    public static final byte TYPE_FIELDID                = 0x08;
    public static final byte TYPE_METHODID               = 0x09;
    public static final byte TYPE_ANNOTATION             = 0x0A;
    public static final byte TYPE_ANNOTATION_SET         = 0x0B;
    public static final byte TYPE_ANNOTATION_SET_REFLIST = 0x0C;
    public static final byte TYPE_ANNOTATION_DIRECTORY   = 0x0D;
    public static final byte TYPE_ENCODEDVALUE           = 0x0E;
    public static final byte TYPE_CLASSDEF               = 0x0F;
    public static final byte TYPE_CLASSDATA              = 0x10;
    public static final byte TYPE_CODE                   = 0x11;
    public static final byte TYPE_TRY                    = 0x12;
    public static final byte TYPE_CATCH_HANDLER          = 0x13;
    public static final byte TYPE_DEBUGINFO_ITEM         = 0x14;
    public static final byte TYPE_PATCHOP_LIST           = 0x15;
    private ChunkValueType() {
    }
}

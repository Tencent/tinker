/*
 * Copyright (C) 2019. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the BSD 3-Clause License
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * the BSD 3-Clause License for more details.
 */
 
package com.tencent.tinker.build.gradle.extension

public class TinkerArkHotExtension {
    String path;
    String name;

    public TinkerArkHotExtension() {
        path = "arkHot";
        name = "patch.apk";
    }

    @Override
    public String toString() {
        """| path= ${path}
           | name= ${name}
         """.stripMargin()
    }
}
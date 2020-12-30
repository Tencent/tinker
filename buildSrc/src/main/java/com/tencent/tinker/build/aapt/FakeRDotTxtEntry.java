/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.tencent.tinker.build.aapt;

/**
 * An {@link RDotTxtEntry} with fake {@link #idValue}, useful for comparing two resource entries for
 * equality, since {@link RDotTxtEntry#compareTo(RDotTxtEntry)} ignores the id value.
 */
public class FakeRDotTxtEntry extends RDotTxtEntry {

    private static final String FAKE_ID = "0x00000000";

    public FakeRDotTxtEntry(IdType idType, RType type, String name) {
        super(idType, type, name, FAKE_ID);
    }
}

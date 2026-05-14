/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.tencent.tinker.build.patch;

import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tinker.net.dongliu.apk.parser.ApkParser;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourcePackage;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceTable;
import tinker.net.dongliu.apk.parser.struct.resource.Type;

/**
 * Before building a patch package, verifies that resource ids are not misaligned between APKs:
 * every resource id (0xPPTTEEEE) from the old APK must map to the same resource name
 * (packageName/resTypeName/entryName) in the new APK whenever that id still exists there.
 *
 * <p>Typical misalignment cause: rebuilding the APK without a stable-id mapping file, so the same
 * numeric id ends up assigned to a different resource, causing incorrect lookups at runtime.
 *
 * <p>Resource ids are synthesized from resources.arsc using package id, type id, and entry index.
 */
public final class MisalignedResourceIdChecker {

    private MisalignedResourceIdChecker() {}

    /**
     * Scans all resource ids and throws a single exception after the full scan, listing every violation.
     *
     * @throws IOException          if an APK cannot be read
     * @throws TinkerPatchException if one or more resource id misalignments are found (message lists all)
     */
    public static void check(File oldApk, File newApk) throws IOException {
        final List<String> violations = new ArrayList<>();
        final Map<Integer, String> oldIdToKey = buildResourceIdToKeyMap(oldApk, violations);
        final Map<Integer, String> newIdToKey = buildResourceIdToKeyMap(newApk, violations);

        for (Map.Entry<Integer, String> e : oldIdToKey.entrySet()) {
            final int id = e.getKey();
            final String oldKey = e.getValue();
            final String newKey = newIdToKey.get(id);
            // id absent in newApk means the resource was removed, which is allowed
            if (newKey != null && !Objects.equals(oldKey, newKey)) {
                violations.add(String.format(
                    "resource name mismatch for id 0x%08x: old=[%s] new=[%s]",
                    id, oldKey, newKey));
            }
        }

        if (!violations.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Misaligned resource id check failed (").append(violations.size()).append(" issue(s)):");
            for (String line : violations) {
                sb.append('\n').append("  - ").append(line);
            }
            sb.append("\npatch generation aborted. Check if the resource mapping file is valid or applied correctly.");
            throw new TinkerPatchException(sb.toString());
        }
    }

    private static Map<Integer, String> buildResourceIdToKeyMap(File apk, List<String> violations) throws IOException {
        ApkParser parser = null;
        try {
            parser = new ApkParser(apk);
            parser.parseResourceTable();
            final ResourceTable table = parser.getResourceTable();
            if (table == null) {
                violations.add("missing resource table in " + apk.getAbsolutePath());
                return new HashMap<>();
            }
            final Map<Integer, String> map = new HashMap<>();
            final Map<String, ResourcePackage> pkgNameMap = table.getPackageNameMap();
            if (pkgNameMap == null) {
                return map;
            }
            final String apkLabel = apk.getAbsolutePath();
            for (ResourcePackage pkg : pkgNameMap.values()) {
                final String packageName = pkg.getName();
                final short pkgId = pkg.getId();
                final Map<String, List<Type>> typesByName = pkg.getTypesNameMap();
                if (typesByName == null) {
                    continue;
                }
                for (Map.Entry<String, List<Type>> typeListEntry : typesByName.entrySet()) {
                    final String resTypeName = typeListEntry.getKey();
                    final List<Type> types = typeListEntry.getValue();
                    if (types == null) {
                        continue;
                    }
                    for (Type type : types) {
                        type.parseAllResourceEntry();
                        final long[] offsets = type.getOffsets();
                        if (offsets == null) {
                            continue;
                        }
                        final short typeId = type.getId();
                        for (int i = 0; i < offsets.length; i++) {
                            final ResourceEntry entry = type.getResourceEntry(i);
                            if (entry == null) {
                                continue;
                            }
                            final String entryKey = entry.getKey();
                            if (entryKey == null) {
                                continue;
                            }
                            final int resourceId =
                                ((pkgId & 0xff) << 24) | ((typeId & 0xff) << 16) | (i & 0xffff);
                            final String fullKey = packageName + "/" + resTypeName + "/" + entryKey;
                            putConsistentIdToKey(map, resourceId, fullKey, apkLabel, violations);
                        }
                    }
                }
            }
            return map;
        } finally {
            IOHelper.closeQuietly(parser);
        }
    }

    /**
     * Records the id → resource-name mapping; conflicting names for the same id within one APK are
     * added to {@code violations}.
     */
    private static void putConsistentIdToKey(
        Map<Integer, String> map,
        int resourceId,
        String key,
        String apkPath,
        List<String> violations
    ) {
        if (map.containsKey(resourceId)) {
            final String existing = map.get(resourceId);
            if (!existing.equals(key)) {
                violations.add(String.format(
                    "inconsistent resource name for id 0x%08x inside apk %s: [%s] vs [%s]",
                    resourceId, apkPath, existing, key));
            }
            return;
        }
        map.put(resourceId, key);
    }
}

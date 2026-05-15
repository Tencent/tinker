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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import tinker.net.dongliu.apk.parser.ApkParser;
import tinker.net.dongliu.apk.parser.parser.BinaryXmlParser;
import tinker.net.dongliu.apk.parser.parser.XmlStreamer;
import tinker.net.dongliu.apk.parser.struct.ResValue;
import tinker.net.dongliu.apk.parser.struct.ResourceValue;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceMapEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourcePackage;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceTable;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceTableMap;
import tinker.net.dongliu.apk.parser.struct.resource.Type;
import tinker.net.dongliu.apk.parser.struct.xml.Attribute;
import tinker.net.dongliu.apk.parser.struct.xml.Attributes;
import tinker.net.dongliu.apk.parser.struct.xml.XmlCData;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import tinker.net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

/**
 * 在生成补丁包前，验证新旧 APK 的资源 id 之间没有错位（misalignment）。
 *
 * <p>错位是指同一个数字 id（0xPPTTEEEE）在新 APK 中被重新分配给了不同的资源——
 * 通常由于重新打包时没有使用稳定的 id 映射文件导致。运行时代码按数字查找 id，
 * 因此重新分配的 id 会静默地使用错误的资源。
 *
 * <p>普通的内容变更（字符串更新、布局修改）是预期行为，不会触发报错。具体规则如下：
 * <ol>
 *   <li>同一 id 对应的类型（typeName）改变 → 违规（type mismatch）。
 *   <li>类型和名称（entryKey）均相同 → 正常补丁，允许。
 *   <li>类型相同但名称不同：
 *     <ul>
 *       <li>File-backed 资源（layout、drawable 等）：逐配置取出 APK 中的文件内容，
 *           尝试按 binary XML 解析并比对节点结构；属性值为资源 id 的，
 *           通过各自 APK 的 ResourceIdentity 规范化后再比较；内容不同则报违规。
 *           若双方均解析失败（如两个 PNG），回退到 SHA-256 比较原始字节，
 *           不同则报违规。若一侧解析成功而另一侧失败，说明文件格式已改变，报违规。
 *           若某配置下一侧文件在 APK 中不存在（但另一侧存在），也报违规。
 *           若双方均不存在则跳过该配置。
 *       <li>Simple-value 资源（string、integer、color 数值等）：逐配置比对值，
 *           全部相同视为安全重命名，否则报违规。
 *     </ul>
 * </ol>
 *
 * <p>File-backed 的判断依据：资源 id 的 type 部分（typeName）属于已知的文件型类型集合
 * （layout、drawable、mipmap 等），而非通过 IO 读取 APK zip 文件来探测。
 *
 * <p>map 条目（style、attr）内的 REFERENCE / ATTRIBUTE 值存储为原始的
 * {@code @ref:0xNNNNNNNN} id 字面量——不递归解析。被引用资源本身的 id 会在
 * 其对应的 id 检查时被发现。
 */
public final class MisalignedResourceIdChecker {

    private MisalignedResourceIdChecker() {}

    /**
     * 已知的 file-backed 资源类型名称集合。
     *
     * <p>这些类型的资源条目值是 APK zip 内文件的路径字符串，需要通过读取文件内容
     * 来比对等价性；与此相对的是 simple-value 类型（string、integer、dimen 等），
     * 其值直接作为字符串比较。
     *
     * <p>此处的类型名与 {@code resources.arsc} 的 type 字段对应，均为小写。
     */
    private static final Set<String> FILE_BACKED_TYPE_NAMES = new HashSet<>(Arrays.asList(
        "layout", "drawable", "mipmap", "anim", "animator", "interpolator",
        "menu", "raw", "xml", "font", "transition", "navigation", "color"
    ));

    /**
     * 扫描 {@code oldApk} 中所有资源 id，验证每个在 {@code newApk} 中仍存在的 id
     * 没有被重新分配给不同的资源。
     *
     * @throws IOException          任一 APK 无法读取时抛出
     * @throws TinkerPatchException 检测到一处或多处资源 id 错位时抛出
     */
    public static void check(File oldApk, File newApk) throws IOException {
        ApkParser oldParser = null;
        ApkParser newParser = null;
        try {
            oldParser = new ApkParser(oldApk);
            oldParser.parseResourceTable();
            newParser = new ApkParser(newApk);
            newParser.parseResourceTable();

            final List<String> violations = new ArrayList<>();
            final Map<Integer, ResourceIdentity> oldMap =
                buildResourceIdentityMap(oldParser, oldApk.getAbsolutePath(), violations);
            final Map<Integer, ResourceIdentity> newMap =
                buildResourceIdentityMap(newParser, newApk.getAbsolutePath(), violations);

            for (Map.Entry<Integer, ResourceIdentity> e : oldMap.entrySet()) {
                final int id = e.getKey();
                final ResourceIdentity oldId = e.getValue();
                final ResourceIdentity newId = newMap.get(id);
                // id 在新 APK 中不存在表示资源被删除 — 允许
                if (newId == null) {
                    continue;
                }
                // Rule 1：类型改变始终是违规
                if (!oldId.typeName.equals(newId.typeName)) {
                    violations.add(String.format(
                        "id 0x%08x: type changed from [%s/%s] to [%s/%s]",
                        id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey));
                    continue;
                }
                // Rule 2：类型和名称均相同 — 正常补丁，内容可自由变更
                if (oldId.entryKey.equals(newId.entryKey)) {
                    continue;
                }
                // Rule 3：类型相同，名称不同 — 根据内容可比性决定
                if (oldId.configSimpleValues == null || newId.configSimpleValues == null) {
                    // 至少一侧是 file-backed；双方都有文件路径时尝试逐配置解析比对
                    if (!oldId.configFilePaths.isEmpty() && !newId.configFilePaths.isEmpty()) {
                        checkXmlFilesMatch(id, oldId, newId, oldParser, newParser,
                            oldMap, newMap, violations);
                    }
                    // 一侧无文件路径（map 类型被标为 file-backed 等边缘情况）：无法比对，跳过
                    continue;
                }
                // 双方均为 simple-value，比对各配置的值
                if (!oldId.configSimpleValues.equals(newId.configSimpleValues)) {
                    violations.add(String.format(
                        "id 0x%08x: resource changed from [%s/%s] to [%s/%s]"
                            + " with different content:\n    old=%s\n    new=%s",
                        id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                        oldId.configSimpleValues, newId.configSimpleValues));
                }
                // 值相同 — 安全重命名，允许
            }

            if (!violations.isEmpty()) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Misaligned resource id check failed (")
                    .append(violations.size()).append(" issue(s)):");
                for (String line : violations) {
                    sb.append('\n').append("  - ").append(line);
                }
                sb.append("\nPatch generation aborted."
                    + " Check if the resource mapping file is valid or applied correctly.");
                throw new TinkerPatchException(sb.toString());
            }
        } finally {
            IOHelper.closeQuietly(oldParser);
            IOHelper.closeQuietly(newParser);
        }
    }

    /**
     * 为 APK 中每个资源条目构建 resourceId → {@link ResourceIdentity} 的映射。
     *
     * <p>资源 id 按 {@code (pkgId << 24) | (typeId << 16) | entryIndex} 合成。
     * 同一 (pkgId, typeId) 下可能存在多个 {@link Type} 对象，代表不同配置变体
     * （语言、密度等）；simple value 按 configKey 分别累积。若 typeName 属于
     * {@link #FILE_BACKED_TYPE_NAMES}，整个资源的
     * {@link ResourceIdentity#configSimpleValues} 置为 {@code null}，
     * 并将各配置的文件路径记录在 {@link ResourceIdentity#configFilePaths}。
     *
     * @param parser      已完成 parseResourceTable() 的 ApkParser
     * @param apkLabel    仅用于错误消息的路径标识
     * @param violations  用于收集非致命错误的列表
     */
    private static Map<Integer, ResourceIdentity> buildResourceIdentityMap(
        ApkParser parser, String apkLabel, List<String> violations
    ) {
        final ResourceTable table = parser.getResourceTable();
        if (table == null) {
            violations.add("missing resource table in " + apkLabel);
            return new HashMap<>();
        }
        final Map<String, ResourcePackage> pkgNameMap = table.getPackageNameMap();
        if (pkgNameMap == null) {
            return new HashMap<>();
        }

        final Map<Integer, String> idToTypeName = new HashMap<>();
        final Map<Integer, String> idToEntryKey = new HashMap<>();
        final Set<Integer> fileBackedIds = new HashSet<>();
        final Map<Integer, Map<String, String>> idToConfigValues = new HashMap<>();
        final Map<Integer, Map<String, String>> idToConfigFilePaths = new HashMap<>();

        for (ResourcePackage pkg : pkgNameMap.values()) {
            final short pkgId = pkg.getId();
            final Map<String, List<Type>> typesByName = pkg.getTypesNameMap();
            if (typesByName == null) {
                continue;
            }
            for (Map.Entry<String, List<Type>> typeEntry : typesByName.entrySet()) {
                final String typeName = typeEntry.getKey();
                final List<Type> types = typeEntry.getValue();
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
                    final String configKey = toHex(type.getConfig().getArray());
                    for (int i = 0; i < offsets.length; i++) {
                        final ResourceEntry entry = type.getResourceEntry(i);
                        if (entry == null) {
                            continue;
                        }
                        final int resourceId =
                            ((pkgId & 0xff) << 24) | ((typeId & 0xff) << 16) | (i & 0xffff);
                        idToTypeName.putIfAbsent(resourceId, typeName);
                        idToEntryKey.putIfAbsent(resourceId, entry.getKey());

                        if (fileBackedIds.contains(resourceId)) {
                            // 已知是 file-backed；补充记录此配置的文件路径
                            recordFilePath(entry, configKey, resourceId, idToConfigFilePaths);
                            continue;
                        }

                        // 根据 typeName 判断是否为 file-backed，避免逐条目走 IO 检测
                        if (FILE_BACKED_TYPE_NAMES.contains(typeName)) {
                            fileBackedIds.add(resourceId);
                            idToConfigValues.remove(resourceId);
                            recordFilePath(entry, configKey, resourceId, idToConfigFilePaths);
                            continue;
                        }

                        final String simpleValue = computeSimpleValue(entry);
                        if (simpleValue == null) {
                            fileBackedIds.add(resourceId);
                            idToConfigValues.remove(resourceId);
                            recordFilePath(entry, configKey, resourceId, idToConfigFilePaths);
                        } else {
                            idToConfigValues
                                .computeIfAbsent(resourceId, k -> new HashMap<>())
                                .put(configKey, simpleValue);
                        }
                    }
                }
            }
        }

        final Map<Integer, ResourceIdentity> result = new HashMap<>();
        for (Map.Entry<Integer, String> entry : idToTypeName.entrySet()) {
            final int id = entry.getKey();
            final Map<String, String> configValues =
                fileBackedIds.contains(id) ? null : idToConfigValues.get(id);
            final Map<String, String> configFilePaths =
                idToConfigFilePaths.getOrDefault(id, new HashMap<>());
            result.put(id, new ResourceIdentity(
                entry.getValue(), idToEntryKey.get(id), configValues, configFilePaths));
        }
        return result;
    }

    /**
     * 若 entry 是 file-backed 的简单 STRING 条目（非 map），将其文件路径记录到
     * {@code idToConfigFilePaths} 中对应配置下。
     */
    private static void recordFilePath(
        ResourceEntry entry, String configKey, int resourceId,
        Map<Integer, Map<String, String>> idToConfigFilePaths
    ) {
        if (entry instanceof ResourceMapEntry) {
            return;
        }
        final ResourceValue val = entry.getValue();
        if (val == null || val.getDataType() != ResValue.ResType.STRING) {
            return;
        }
        final String filePath = val.toStringValue();
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        idToConfigFilePaths
            .computeIfAbsent(resourceId, k -> new HashMap<>())
            .put(configKey, filePath);
    }

    /**
     * 计算资源条目的可比较 simple-value 字符串；若条目是 file-backed 则返回 {@code null}。
     *
     * <p>file-backed 的判断已在调用方通过 typeName 完成；此方法仅处理 simple-value 类型，
     * 在遇到边缘情况（map 条目内出现文件路径字符串）时仍可返回 {@code null}。
     *
     * <ul>
     *   <li>复杂 map 条目（{@link ResourceMapEntry}）：通过 {@link #serializeMapEntry}
     *       序列化。
     *   <li>REFERENCE / ATTRIBUTE：原始 id 以 {@code @ref:0xNNNNNNNN} 形式返回，
     *       不递归解析；被引用 id 的错位会在其本身的 id 检查时被发现。
     *   <li>其他类型：{@link ResourceEntry#toStringValue()}。
     * </ul>
     */
    private static String computeSimpleValue(ResourceEntry entry) {
        if (entry instanceof ResourceMapEntry) {
            return serializeMapEntry((ResourceMapEntry) entry);
        }
        final ResourceValue value = entry.getValue();
        if (value == null) {
            return "null";
        }
        final short dataType = value.getDataType();
        if (dataType == ResValue.ResType.REFERENCE || dataType == ResValue.ResType.ATTRIBUTE) {
            final long refId =
                ((ResourceValue.ReferenceResourceValue) value).getReferenceResourceId();
            return String.format("@ref:0x%08x", (int) refId);
        }
        return entry.toStringValue();
    }

    /**
     * 将复杂 map 条目（style、attr 定义、plural、array 等）序列化为
     * {@code {0xNNNNNNNN=value,...}} 格式的字符串，按 nameRef 排序确保结果确定性。
     *
     * <p>REFERENCE / ATTRIBUTE 值以 {@code @ref:0xNNNNNNNN} 形式写入，不递归解析。
     */
    private static String serializeMapEntry(ResourceMapEntry mapEntry) {
        final ResourceTableMap[] maps = mapEntry.getResourceTableMaps();
        if (maps == null || maps.length == 0) {
            return "{}";
        }
        final TreeMap<String, String> sorted = new TreeMap<>();
        for (ResourceTableMap tableMap : maps) {
            final String nameKey = String.format("0x%08x", tableMap.getNameRef());
            final ResourceValue resValue = tableMap.getResValue();
            final String valueStr;
            if (resValue == null) {
                valueStr = "null";
            } else {
                final short dataType = resValue.getDataType();
                if (dataType == ResValue.ResType.REFERENCE
                    || dataType == ResValue.ResType.ATTRIBUTE) {
                    final long refId =
                        ((ResourceValue.ReferenceResourceValue) resValue).getReferenceResourceId();
                    valueStr = String.format("@ref:0x%08x", (int) refId);
                } else {
                    final String raw = resValue.toStringValue();
                    valueStr = raw != null ? raw : "null";
                }
            }
            sorted.put(nameKey, valueStr);
        }
        final StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 逐配置比对两个 file-backed 资源的 XML 结构，并将违规或异常直接写入
     * {@code violations}。
     *
     * <p>对双方共有的配置（configKey 相同），各取出对应的文件尝试解析为 binary XML
     * 并比较规范化的节点事件序列。属性值为资源 id 引用的，通过各自 APK 的
     * ResourceIdentity 规范化为 typeName/entryKey，消除 id 数值差异的干扰。
     *
     * <p>若某配置下一侧文件存在而另一侧为 null（在 APK 中找不到），将其记录为
     * 违规——因为文件路径来自资源表中的 STRING 值，能取到路径就意味着资源表已声明
     * 该文件，文件却不存在说明 APK 不一致。若双方均为 null，则跳过该配置。
     */
    private static void checkXmlFilesMatch(
        int id,
        ResourceIdentity oldId,
        ResourceIdentity newId,
        ApkParser oldParser,
        ApkParser newParser,
        Map<Integer, ResourceIdentity> oldMap,
        Map<Integer, ResourceIdentity> newMap,
        List<String> violations
    ) throws IOException {
        for (Map.Entry<String, String> e : oldId.configFilePaths.entrySet()) {
            final String configKey = e.getKey();
            final String oldPath = e.getValue();
            final String newPath = newId.configFilePaths.get(configKey);
            if (newPath == null) {
                continue; // 配置仅存在于旧 APK — 允许
            }
            final byte[] oldData = oldParser.getFileData(oldPath);
            final byte[] newData = newParser.getFileData(newPath);
            if (oldData == null && newData == null) {
                continue; // 双方文件均不存在 — 跳过
            }
            if (oldData == null) {
                violations.add(String.format(
                    "id 0x%08x: resource changed from [%s/%s] to [%s/%s]:"
                        + " old file '%s' not found in APK (config %s)",
                    id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                    oldPath, configKey));
                continue;
            }
            if (newData == null) {
                violations.add(String.format(
                    "id 0x%08x: resource changed from [%s/%s] to [%s/%s]:"
                        + " new file '%s' not found in APK (config %s)",
                    id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                    newPath, configKey));
                continue;
            }
            final List<String> oldEvents =
                collectXmlEvents(oldData, oldParser.getResourceTable(), oldMap);
            final List<String> newEvents =
                collectXmlEvents(newData, newParser.getResourceTable(), newMap);
            if (oldEvents == null && newEvents == null) {
                // 双方均非 binary XML（如两个 PNG），回退到 SHA-256 比较原始字节
                final String oldSha256 = sha256Hex(oldData);
                final String newSha256 = sha256Hex(newData);
                if (!oldSha256.equals(newSha256)) {
                    violations.add(String.format(
                        "id 0x%08x: resource changed from [%s/%s] to [%s/%s]"
                            + " with different file content (SHA-256, config %s):\n"
                            + "    old file=%s  sha256=%s\n"
                            + "    new file=%s  sha256=%s",
                        id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                        configKey, oldPath, oldSha256, newPath, newSha256));
                }
                continue;
            }
            if (oldEvents == null) {
                // 旧文件无法解析为 binary XML，但新文件可以 — 文件格式已改变
                violations.add(String.format(
                    "id 0x%08x: resource changed from [%s/%s] to [%s/%s]:"
                        + " old file '%s' is not a valid binary XML (config %s)",
                    id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                    oldPath, configKey));
                continue;
            }
            if (newEvents == null) {
                // 新文件无法解析为 binary XML，但旧文件可以 — 文件格式已改变
                violations.add(String.format(
                    "id 0x%08x: resource changed from [%s/%s] to [%s/%s]:"
                        + " new file '%s' is not a valid binary XML (config %s)",
                    id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                    newPath, configKey));
                continue;
            }
            if (!oldEvents.equals(newEvents)) {
                violations.add(String.format(
                    "id 0x%08x: resource changed from [%s/%s] to [%s/%s]"
                        + " with different file structure (config %s):\n"
                        + "    old file=%s\n    new file=%s",
                    id, oldId.typeName, oldId.entryKey, newId.typeName, newId.entryKey,
                    configKey, oldPath, newPath));
            }
        }
    }

    /**
     * 解析 binary XML 并收集规范化的节点事件序列。
     *
     * <p>REFERENCE / ATTRIBUTE 类型的属性值通过 identityMap 规范化为
     * {@code typeName/entryKey}，使得跨 APK 比较时资源 id 数值的差异不影响
     * 结构等价性判断。
     *
     * @return 事件序列；若解析过程中抛出异常（如文件不是 binary XML）则返回 {@code null}
     */
    private static List<String> collectXmlEvents(
        byte[] data, ResourceTable table, Map<Integer, ResourceIdentity> identityMap
    ) {
        final List<String> events = new ArrayList<>();
        final BinaryXmlParser xmlParser = new BinaryXmlParser(ByteBuffer.wrap(data), table);
        xmlParser.setXmlStreamer(new XmlNodeCollector(events, identityMap));
        try {
            xmlParser.parse();
        } catch (Exception ignored) {
            // 解析失败（如 PNG 等非 binary XML 文件），由调用方回退到 SHA-256 比较
            return null;
        }
        return events;
    }

    /**
     * 计算字节数组的 SHA-256 摘要并以小写十六进制字符串返回。
     */
    private static String sha256Hex(byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 必须支持的算法，此分支实际不可达
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** 编码字节数组为小写十六进制字符串。 */
    private static String toHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 收集 binary XML 的节点事件，并规范化属性值中的资源 id 引用。
     * 仅关注 START_ELEMENT 和 END_ELEMENT，忽略 namespace 声明和 CData。
     */
    private static final class XmlNodeCollector implements XmlStreamer {
        private final List<String> mEvents;
        private final Map<Integer, ResourceIdentity> mIdentityMap;

        XmlNodeCollector(List<String> events, Map<Integer, ResourceIdentity> identityMap) {
            mEvents = events;
            mIdentityMap = identityMap;
        }

        @Override
        public void onStartTag(XmlNodeStartTag tag) {
            // 将属性按名称排序，消除属性顺序差异的影响
            final TreeMap<String, String> sortedAttributes = new TreeMap<>();
            final Attributes attributes = tag.getAttributes();
            if (attributes != null) {
                for (Attribute attr : attributes.value()) {
                    if (attr == null) {
                        continue;
                    }
                    final String attrKey = attr.getNamespace() != null
                        ? attr.getNamespace() + ":" + attr.getName()
                        : attr.getName();
                    sortedAttributes.put(attrKey, normalizeAttrValue(attr));
                }
            }
            final StringBuilder sb = new StringBuilder("<").append(tag.getName());
            for (Map.Entry<String, String> attrEntry : sortedAttributes.entrySet()) {
                sb.append(' ').append(attrEntry.getKey())
                    .append("=\"").append(attrEntry.getValue()).append('"');
            }
            sb.append('>');
            mEvents.add(sb.toString());
        }

        @Override
        public void onEndTag(XmlNodeEndTag tag) {
            mEvents.add("</" + tag.getName() + '>');
        }

        @Override
        public void onCData(XmlCData cdata) {
            // BinaryXmlParser 不触发此回调 — 无操作
        }

        @Override
        public void onNamespaceStart(XmlNamespaceStartTag tag) {
            // namespace 声明不影响结构等价性 — 无操作
        }

        @Override
        public void onNamespaceEnd(XmlNamespaceEndTag tag) {
            // 无操作
        }

        @Override
        public void onAttribute(Attribute attribute) {
            // BinaryXmlParser 不通过此回调分发属性（属性随 onStartTag 一起到达）— 无操作
        }

        /**
         * 规范化属性值：REFERENCE / ATTRIBUTE 类型替换为 {@code @typeName/entryKey}，
         * 其他类型取 {@link Attribute#toStringValue()}。
         */
        private String normalizeAttrValue(Attribute attr) {
            final ResourceValue typedValue = attr.getTypedValue();
            if (typedValue != null) {
                final short dataType = typedValue.getDataType();
                if (dataType == ResValue.ResType.REFERENCE
                    || dataType == ResValue.ResType.ATTRIBUTE) {
                    final long refId =
                        ((ResourceValue.ReferenceResourceValue) typedValue).getReferenceResourceId();
                    final ResourceIdentity identity = mIdentityMap.get((int) refId);
                    if (identity != null) {
                        return "@" + identity.typeName + "/" + identity.entryKey;
                    }
                    // 系统资源 id 或 identity map 中未收录的 id — 保留原始数值
                    return String.format("@ref:0x%08x", (int) refId);
                }
            }
            return attr.toStringValue();
        }
    }

    /**
     * 单个资源 id 的身份信息。
     *
     * <p>{@code configSimpleValues} 为 {@code null} 表示该资源是 file-backed（layout、
     * drawable 等）；此时 {@code configFilePaths} 记录了各配置对应的文件路径，
     * 供 XML 结构比对使用（始终非 null，可能为空 map）。
     */
    private static final class ResourceIdentity {
        final String typeName;
        final String entryKey;
        /** null 表示至少一个配置是 file-backed */
        final Map<String, String> configSimpleValues;
        /** 各配置的文件路径（仅简单 STRING 条目，不含 map 条目）；始终非 null */
        final Map<String, String> configFilePaths;

        ResourceIdentity(
            String typeName,
            String entryKey,
            Map<String, String> configSimpleValues,
            Map<String, String> configFilePaths
        ) {
            this.typeName = typeName;
            this.entryKey = entryKey;
            this.configSimpleValues = configSimpleValues;
            this.configFilePaths = configFilePaths;
        }
    }
}

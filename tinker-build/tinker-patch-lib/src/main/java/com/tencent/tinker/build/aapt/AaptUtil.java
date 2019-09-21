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

import com.tencent.tinker.build.aapt.RDotTxtEntry.IdType;
import com.tencent.tinker.build.aapt.RDotTxtEntry.RType;
import com.tencent.tinker.commons.util.IOHelper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public final class AaptUtil {

    private static final String ID_DEFINITION_PREFIX = "@+id/";
    private static final String ITEM_TAG             = "item";

    private static final XPathExpression ANDROID_ID_USAGE = createExpression("//@*[starts-with(., '@') and " + "not(starts-with(., '@+')) and " + "not(starts-with(., '@android:')) and " + "not(starts-with(., '@null'))]");

    private static final XPathExpression ANDROID_ID_DEFINITION = createExpression("//@*[starts-with(., '@+') and " + "not(starts-with(., '@+android:id')) and " + "not(starts-with(., '@+id/android:'))]");

    private static final Map<String, RType> RESOURCE_TYPES = getResourceTypes();
    private static final List<String>       IGNORED_TAGS   = Arrays.asList("eat-comment", "skip");

    private static XPathExpression createExpression(String expressionStr) {
        try {
            return XPathFactory.newInstance().newXPath().compile(expressionStr);
        } catch (XPathExpressionException e) {
            throw new AaptUtilException(e);
        }
    }

    private static Map<String, RType> getResourceTypes() {
        Map<String, RType> types = new HashMap<String, RType>();
        for (RType rType : RType.values()) {
            types.put(rType.toString(), rType);
        }
        types.put("string-array", RType.ARRAY);
        types.put("integer-array", RType.ARRAY);
        types.put("declare-styleable", RType.STYLEABLE);
        return types;
    }

    public static AaptResourceCollector collectResource(List<String> resourceDirectoryList) {
        return collectResource(resourceDirectoryList, null);
    }

    public static AaptResourceCollector collectResource(List<String> resourceDirectoryList, Map<RType, Set<RDotTxtEntry>> rTypeResourceMap) {
        AaptResourceCollector resourceCollector = new AaptResourceCollector(rTypeResourceMap);
        List<RDotTxtEntry> references = new ArrayList<>();
        for (String resourceDirectory : resourceDirectoryList) {
            try {
                collectResources(resourceDirectory, resourceCollector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (String resourceDirectory : resourceDirectoryList) {
            try {
                processXmlFilesForIds(resourceDirectory, references, resourceCollector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return resourceCollector;
    }

    public static void processXmlFilesForIds(String resourceDirectory, List<RDotTxtEntry> references, AaptResourceCollector resourceCollector) throws Exception {
        List<String> xmlFullFilenameList = FileUtil.findMatchFile(resourceDirectory, Constant.Symbol.DOT + Constant.File.XML);
        if (xmlFullFilenameList != null) {
            for (String xmlFullFilename : xmlFullFilenameList) {
                File xmlFile = new File(xmlFullFilename);
                String parentFullFilename = xmlFile.getParent();
                File parentFile = new File(parentFullFilename);
                if (isAValuesDirectory(parentFile.getName()) || parentFile.getName().startsWith("raw")) {
                    // Ignore files under values* directories and raw*.
                    continue;
                }
                processXmlFile(xmlFullFilename, references, resourceCollector);
            }
        }
    }

    private static void collectResources(String resourceDirectory, AaptResourceCollector resourceCollector) throws Exception {
        File resourceDirectoryFile = new File(resourceDirectory);
        File[] fileArray = resourceDirectoryFile.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                if (file.isDirectory()) {
                    String directoryName = file.getName();
                    if (directoryName.startsWith("values")) {
                        if (!isAValuesDirectory(directoryName)) {
                            throw new AaptUtilException("'" + directoryName + "' is not a valid values directory.");
                        }
                        processValues(file.getAbsolutePath(), resourceCollector);
                    } else {
                        processFileNamesInDirectory(file.getAbsolutePath(), resourceCollector);
                    }
                }
            }
        }
    }

    /**
     * is a value directory
     *
     * @param directoryName
     * @return boolean
     */
    public static boolean isAValuesDirectory(String directoryName) {
        if (directoryName == null) {
            throw new NullPointerException("directoryName can not be null");
        }
        return directoryName.equals("values") || directoryName.startsWith("values-");
    }

    public static void processFileNamesInDirectory(String resourceDirectory, AaptResourceCollector resourceCollector) throws IOException {
        File resourceDirectoryFile = new File(resourceDirectory);
        String directoryName = resourceDirectoryFile.getName();
        int dashIndex = directoryName.indexOf('-');
        if (dashIndex != -1) {
            directoryName = directoryName.substring(0, dashIndex);
        }

        if (!RESOURCE_TYPES.containsKey(directoryName)) {
            throw new AaptUtilException(resourceDirectoryFile.getAbsolutePath() + " is not a valid resource sub-directory.");
        }
        File[] fileArray = resourceDirectoryFile.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                if (file.isHidden()) {
                    continue;
                }
                String filename = file.getName();
                int dotIndex = filename.indexOf('.');
                String resourceName = dotIndex != -1 ? filename.substring(0, dotIndex) : filename;

                RType rType = RESOURCE_TYPES.get(directoryName);
                resourceCollector.addIntResourceIfNotPresent(rType, resourceName);
                ResourceDirectory resourceDirectoryBean = new ResourceDirectory(file.getParentFile().getName(), file.getAbsolutePath());
                resourceCollector.addRTypeResourceName(rType, resourceName, null, resourceDirectoryBean);
            }
        }
    }

    public static void processValues(String resourceDirectory, AaptResourceCollector resourceCollector) throws Exception {
        File resourceDirectoryFile = new File(resourceDirectory);
        File[] fileArray = resourceDirectoryFile.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                if (file.isHidden()) {
                    continue;
                }
                if (!file.isFile()) {
                    // warning
                    continue;
                }
                processValuesFile(file.getAbsolutePath(), resourceCollector);
            }
        }
    }

    public static void processValuesFile(String valuesFullFilename, AaptResourceCollector resourceCollector) throws Exception {
        Document document = JavaXmlUtil.parse(valuesFullFilename);
        String directoryName = new File(valuesFullFilename).getParentFile().getName();
        Element root = document.getDocumentElement();

        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String resourceType = node.getNodeName();
            if (resourceType.equals(ITEM_TAG)) {
                resourceType = node.getAttributes().getNamedItem("type").getNodeValue();
                if (resourceType.equals("id")) {
                    resourceCollector.addIgnoreId(node.getAttributes().getNamedItem("name").getNodeValue());
                }
            }

            if (IGNORED_TAGS.contains(resourceType)) {
                continue;
            }

            if (!RESOURCE_TYPES.containsKey(resourceType)) {
                throw new AaptUtilException("Invalid resource type '<" + resourceType + ">' in '" + valuesFullFilename + "'.");
            }

            RType rType = RESOURCE_TYPES.get(resourceType);
            String resourceValue = null;
            switch (rType) {
                case STRING:
                case COLOR:
                case DIMEN:
                case DRAWABLE:
                case BOOL:
                case INTEGER:
                    resourceValue = node.getTextContent().trim();
                    break;
                case ARRAY://has sub item
                case PLURALS://has sub item
                case STYLE://has sub item
                case STYLEABLE://has sub item
                    resourceValue = subNodeToString(node);
                    break;
                case FRACTION://no sub item
                    resourceValue = nodeToString(node, true);
                    break;
                case ATTR://no sub item
                    resourceValue = nodeToString(node, true);
                    break;
                default:
                    break;
            }
            try {
                addToResourceCollector(resourceCollector, new ResourceDirectory(directoryName, valuesFullFilename), node, rType, resourceValue);
            } catch (Exception e) {
                throw new AaptUtilException(e.getMessage() + ",Process file error:" + valuesFullFilename, e);
            }
        }
    }

    public static void processXmlFile(String xmlFullFilename, List<RDotTxtEntry> references, AaptResourceCollector resourceCollector) throws IOException, XPathExpressionException {
        Document document = JavaXmlUtil.parse(xmlFullFilename);
        NodeList nodesWithIds = (NodeList) ANDROID_ID_DEFINITION.evaluate(document, XPathConstants.NODESET);
        for (int i = 0; i < nodesWithIds.getLength(); i++) {
            String resourceName = nodesWithIds.item(i).getNodeValue();
            if (!resourceName.startsWith(ID_DEFINITION_PREFIX)) {
                throw new AaptUtilException("Invalid definition of a resource: '" + resourceName + "'");
            }

            resourceCollector.addIntResourceIfNotPresent(RType.ID, resourceName.substring(ID_DEFINITION_PREFIX.length()));
        }

        NodeList nodesUsingIds = (NodeList) ANDROID_ID_USAGE.evaluate(document, XPathConstants.NODESET);
        for (int i = 0; i < nodesUsingIds.getLength(); i++) {
            String resourceName = nodesUsingIds.item(i).getNodeValue();
            int slashPosition = resourceName.indexOf('/');
            if (slashPosition < 0) {
                continue;
            }
            String rawRType = resourceName.substring(1, slashPosition);
            String name = resourceName.substring(slashPosition + 1);

            if (name.startsWith("android:")) {
                continue;
            }

            if (rawRType.startsWith("tools:")) {
                continue;
            }

            if (!RESOURCE_TYPES.containsKey(rawRType)) {
                throw new AaptUtilException("Invalid reference '" + resourceName + "' in '" + xmlFullFilename + "'");
            }
            RType rType = RESOURCE_TYPES.get(rawRType);

            // if (!resourceCollector.isContainResource(rType, IdType.INT, sanitizeName(resourceCollector, name))) {
            //     throw new AaptUtilException("Not found reference '" + resourceName + "' in '" + xmlFullFilename + "'");
            // }
            references.add(new FakeRDotTxtEntry(IdType.INT, rType, sanitizeName(rType, resourceCollector, name)));
        }
    }

    private static void addToResourceCollector(AaptResourceCollector resourceCollector, ResourceDirectory resourceDirectory, Node node, RType rType, String resourceValue) {
        String resourceName = sanitizeName(rType, resourceCollector, extractNameAttribute(node));
        resourceCollector.addRTypeResourceName(rType, resourceName, resourceValue, resourceDirectory);
        if (rType.equals(RType.STYLEABLE)) {

            int count = 0;
            for (Node attrNode = node.getFirstChild(); attrNode != null; attrNode = attrNode.getNextSibling()) {
                if (attrNode.getNodeType() != Node.ELEMENT_NODE || !attrNode.getNodeName().equals("attr")) {
                    continue;
                }

                String rawAttrName = extractNameAttribute(attrNode);
                String attrName = sanitizeName(rType, resourceCollector, rawAttrName);
                resourceCollector.addResource(RType.STYLEABLE, IdType.INT, String.format("%s_%s", resourceName, attrName), Integer.toString(count++));

                if (!rawAttrName.startsWith("android:")) {
                    resourceCollector.addIntResourceIfNotPresent(RType.ATTR, rawAttrName);
                    resourceCollector.addRTypeResourceName(RType.ATTR, rawAttrName, nodeToString(attrNode, true), resourceDirectory);
                }
            }

            resourceCollector.addIntArrayResourceIfNotPresent(rType, resourceName, count);
        } else {
            resourceCollector.addIntResourceIfNotPresent(rType, resourceName);
        }
    }

    private static String sanitizeName(RType rType, AaptResourceCollector resourceCollector, String rawName) {
        String sanitizeName = rawName.replaceAll("[.:]", "_");
        resourceCollector.putSanitizeName(rType, sanitizeName, rawName);
        return sanitizeName;
    }

    private static String extractNameAttribute(Node node) {
        return node.getAttributes().getNamedItem("name").getNodeValue();
    }

    /**
     * merge package r type resource map
     *
     * @param packageRTypeResourceMapList
     * @return Map<String, Map<RType,Set<RDotTxtEntry>>>
     */
    public static Map<String, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>>> mergePackageRTypeResourceMap(List<PackageRTypeResourceMap> packageRTypeResourceMapList) {
        Map<String, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>>> packageRTypeResourceMergeMap = new HashMap<String, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>>>();
        Map<String, AaptResourceCollector> aaptResourceCollectorMap = new HashMap<String, AaptResourceCollector>();
        for (PackageRTypeResourceMap packageRTypeResourceMap : packageRTypeResourceMapList) {
            String packageName = packageRTypeResourceMap.packageName;
            Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> rTypeResourceMap = packageRTypeResourceMap.rTypeResourceMap;
            AaptResourceCollector aaptResourceCollector = null;
            if (aaptResourceCollectorMap.containsKey(packageName)) {
                aaptResourceCollector = aaptResourceCollectorMap.get(packageName);
            } else {
                aaptResourceCollector = new AaptResourceCollector();
                aaptResourceCollectorMap.put(packageName, aaptResourceCollector);
            }
            Iterator<Entry<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>>> iterator = rTypeResourceMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> entry = iterator.next();
                RType rType = entry.getKey();
                Set<com.tencent.tinker.build.aapt.RDotTxtEntry> rDotTxtEntrySet = entry.getValue();
                for (com.tencent.tinker.build.aapt.RDotTxtEntry rDotTxtEntry : rDotTxtEntrySet) {
                    if (rDotTxtEntry.idType.equals(IdType.INT)) {
                        aaptResourceCollector.addIntResourceIfNotPresent(rType, rDotTxtEntry.name);
                    } else if (rDotTxtEntry.idType.equals(IdType.INT_ARRAY)) {
                        aaptResourceCollector.addResource(rType, rDotTxtEntry.idType, rDotTxtEntry.name, rDotTxtEntry.idValue.trim());
                    }
                }
            }
        }
        Iterator<Entry<String, AaptResourceCollector>> iterator = aaptResourceCollectorMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, AaptResourceCollector> entry = iterator.next();
            packageRTypeResourceMergeMap.put(entry.getKey(), entry.getValue().getRTypeResourceMap());
        }
        return packageRTypeResourceMergeMap;
    }

    /**
     * write R.java
     *
     * @param outputDirectory
     * @param packageName
     * @param rTypeResourceMap
     * @param isFinal
     */
    public static void writeRJava(String outputDirectory, String packageName, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> rTypeResourceMap, boolean isFinal) {
        String outputFullFilename = new File(outputDirectory).getAbsolutePath() + Constant.Symbol.SLASH_LEFT + (packageName.replace(Constant.Symbol.DOT, Constant.Symbol.SLASH_LEFT) + Constant.Symbol.SLASH_LEFT + "R" + Constant.Symbol.DOT + Constant.File.JAVA);
        FileUtil.createFile(outputFullFilename);
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileOutputStream(outputFullFilename));
            writer.format("package %s;\n\n", packageName);
            writer.println("public final class R {\n");
            for (RType rType : rTypeResourceMap.keySet()) {
                // Now start the block for the new type.
                writer.format("  public static final class %s {\n", rType.toString());
                for (com.tencent.tinker.build.aapt.RDotTxtEntry rDotTxtEntry : rTypeResourceMap.get(rType)) {
                    // Write out the resource.
                    // Write as an int.
                    writer.format("    public static%s%s %s=%s;\n", isFinal ? " final " : " ", rDotTxtEntry.idType, rDotTxtEntry.name, rDotTxtEntry.idValue.trim());
                }
                writer.println("  }\n");
            }
            // Close the class definition.
            writer.println("}");
        } catch (Exception e) {
            throw new AaptUtilException(e);
        } finally {
            IOHelper.closeQuietly(writer);
        }
    }

    /**
     * write R.java
     *
     * @param outputDirectory
     * @param packageRTypeResourceMap
     * @param isFinal
     * @throws IOException
     */
    public static void writeRJava(String outputDirectory, Map<String, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>>> packageRTypeResourceMap, boolean isFinal) {
        for (String packageName : packageRTypeResourceMap.keySet()) {
            Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> rTypeResourceMap = packageRTypeResourceMap.get(packageName);
            writeRJava(outputDirectory, packageName, rTypeResourceMap, isFinal);
        }
    }

    private static String subNodeToString(Node node) {
        StringBuilder stringBuilder = new StringBuilder();
        if (node != null) {
            NodeList nodeList = node.getChildNodes();
            stringBuilder.append(nodeToString(node, false));
            stringBuilder.append(StringUtil.CRLF_STRING);
            int nodeListLength = nodeList.getLength();
            for (int i = 0; i < nodeListLength; i++) {
                Node childNode = nodeList.item(i);
                if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                stringBuilder.append(nodeToString(childNode, true));
                stringBuilder.append(StringUtil.CRLF_STRING);
            }
            if (stringBuilder.length() > StringUtil.CRLF_STRING.length()) {
                stringBuilder.delete(stringBuilder.length() - StringUtil.CRLF_STRING.length(), stringBuilder.length());
            }
        }
        return stringBuilder.toString();
    }

    private static String nodeToString(Node node, boolean isNoChild) {
        StringBuilder stringBuilder = new StringBuilder();
        if (node != null) {
            stringBuilder.append(node.getNodeName());
            NamedNodeMap namedNodeMap = node.getAttributes();
            stringBuilder.append(Constant.Symbol.MIDDLE_BRACKET_LEFT);
            int namedNodeMapLength = namedNodeMap.getLength();
            for (int j = 0; j < namedNodeMapLength; j++) {
                Node attributeNode = namedNodeMap.item(j);
                stringBuilder.append(Constant.Symbol.AT + attributeNode.getNodeName() + Constant.Symbol.EQUAL + attributeNode.getNodeValue());
                if (j < namedNodeMapLength - 1) {
                    stringBuilder.append(Constant.Symbol.COMMA);
                }
            }
            stringBuilder.append(Constant.Symbol.MIDDLE_BRACKET_RIGHT);
            String value = StringUtil.nullToBlank(isNoChild ? node.getTextContent() : node.getNodeValue()).trim();
            if (StringUtil.isNotBlank(value)) {
                stringBuilder.append(Constant.Symbol.EQUAL + value);
            }
        }
        return stringBuilder.toString();
    }

    public static class PackageRTypeResourceMap {
        private String                                                      packageName      = null;
        private Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> rTypeResourceMap = null;

        public PackageRTypeResourceMap(String packageName, Map<RType, Set<com.tencent.tinker.build.aapt.RDotTxtEntry>> rTypeResourceMap) {
            this.packageName = packageName;
            this.rTypeResourceMap = rTypeResourceMap;
        }
    }

    public static class AaptUtilException extends RuntimeException {
        private static final long serialVersionUID = 1702278793911780809L;

        public AaptUtilException(String message) {
            super(message);
        }

        public AaptUtilException(Throwable cause) {
            super(cause);
        }

        public AaptUtilException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

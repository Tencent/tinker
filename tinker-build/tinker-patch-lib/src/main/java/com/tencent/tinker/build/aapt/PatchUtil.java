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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatchUtil {

    /**
     * read r txt
     *
     * @param rTxtFullFilename
     * @return Map<RType, Set<RDotTxtEntry>>
     */
    public static Map<RType, Set<RDotTxtEntry>> readRTxt(String rTxtFullFilename) {
        //read base resource entry
        Map<RType, Set<RDotTxtEntry>> rTypeResourceMap = new HashMap<RType, Set<RDotTxtEntry>>();
        if (StringUtil.isNotBlank(rTxtFullFilename) && FileUtil.isExist(rTxtFullFilename)) {
            BufferedReader bufferedReader = null;
            try {
                final Pattern textSymbolLine = Pattern.compile("(\\S+) (\\S+) (\\S+) (.+)");
                bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(rTxtFullFilename)));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    Matcher matcher = textSymbolLine.matcher(line);
                    if (matcher.matches()) {
                        IdType idType = IdType.from(matcher.group(1));
                        RType rType = RType.valueOf(matcher.group(2).toUpperCase());
                        String name = matcher.group(3);
                        String idValue = matcher.group(4);
                        RDotTxtEntry rDotTxtEntry = new RDotTxtEntry(idType, rType, name, idValue);
                        Set<RDotTxtEntry> hashSet = null;
                        if (rTypeResourceMap.containsKey(rType)) {
                            hashSet = rTypeResourceMap.get(rType);
                        } else {
                            hashSet = new HashSet<RDotTxtEntry>();
                            rTypeResourceMap.put(rType, hashSet);
                        }
                        hashSet.add(rDotTxtEntry);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                IOHelper.closeQuietly(bufferedReader);
            }
        }
        return rTypeResourceMap;
    }

    /**
     * generate public resource xml
     *
     * @param aaptResourceCollector
     * @param outputIdsXmlFullFilename
     * @param outputPublicXmlFullFilename
     */
    public static void generatePublicResourceXml(AaptResourceCollector aaptResourceCollector, String outputIdsXmlFullFilename, String outputPublicXmlFullFilename) {
        if (aaptResourceCollector == null) {
            return;
        }
        FileUtil.createFile(outputIdsXmlFullFilename);
        FileUtil.createFile(outputPublicXmlFullFilename);
        PrintWriter idsWriter = null;
        PrintWriter publicWriter = null;
        try {
            FileUtil.createFile(outputIdsXmlFullFilename);
            FileUtil.createFile(outputPublicXmlFullFilename);
            idsWriter = new PrintWriter(new File(outputIdsXmlFullFilename), "UTF-8");
            publicWriter = new PrintWriter(new File(outputPublicXmlFullFilename), "UTF-8");
            idsWriter.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            publicWriter.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            idsWriter.println("<resources>");
            publicWriter.println("<resources>");
            Map<RType, Set<RDotTxtEntry>> map = aaptResourceCollector.getRTypeResourceMap();
            Iterator<Entry<RType, Set<RDotTxtEntry>>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<RType, Set<RDotTxtEntry>> entry = iterator.next();
                RType rType = entry.getKey();
                if (!rType.equals(RType.STYLEABLE)) {
                    Set<RDotTxtEntry> set = entry.getValue();
                    for (RDotTxtEntry rDotTxtEntry : set) {
                        // if (rType.equals(RType.STYLE)) {
                        String rawName = aaptResourceCollector.getRawName(rType, rDotTxtEntry.name);
                        if (StringUtil.isBlank(rawName)) {
                            // System.err.println("Blank?" + rDotTxtEntry.name);
                            rawName = rDotTxtEntry.name;
                        }
                        publicWriter.println("<public type=\"" + rType + "\" name=\"" + rawName + "\" id=\"" + rDotTxtEntry.idValue.trim() + "\" />");
                        // } else {
                        //     publicWriter.println("<public type=\"" + rType + "\" name=\"" + rDotTxtEntry.name + "\" id=\"" + rDotTxtEntry.idValue + "\" />");
                        // }
                    }
                    Set<String> ignoreIdSet = aaptResourceCollector.getIgnoreIdSet();
                    for (RDotTxtEntry rDotTxtEntry : set) {
                        if (rType.equals(RType.ID) && !ignoreIdSet.contains(rDotTxtEntry.name)) {
                            idsWriter.println("<item type=\"" + rType + "\" name=\"" + rDotTxtEntry.name + "\"/>");
                        } else if (rType.equals(RType.STYLE)) {

                            if (rDotTxtEntry.name.indexOf(Constant.Symbol.UNDERLINE) > 0) {
                                // idsWriter.println("<item type=\""+rType+"\" name=\""+(rDotTxtEntry.name.replace(Constant.Symbol.UNDERLINE, Constant.Symbol.DOT))+"\"/>");
                            }
                        }
                    }
                }
                idsWriter.flush();
                publicWriter.flush();
            }
            idsWriter.println("</resources>");
            publicWriter.println("</resources>");
        } catch (Exception e) {
            throw new PatchUtilException(e);
        } finally {
            if (idsWriter != null) {
                idsWriter.flush();
                idsWriter.close();
            }
            if (publicWriter != null) {
                publicWriter.flush();
                publicWriter.close();
            }
        }
    }

    public static class PublicResourceEntry {
        private RType  rType        = null;
        private String resourceName = null;

        public PublicResourceEntry(RType rType, String resourceName) {
            this.rType = rType;
            this.resourceName = resourceName;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof PublicResourceEntry)) {
                return false;
            }
            PublicResourceEntry that = (PublicResourceEntry) obj;
            return ObjectUtil.equal(this.rType, that.rType) && ObjectUtil.equal(this.resourceName, that.resourceName);
        }

        public int hashCode() {
            return Arrays.hashCode(new Object[]{this.rType, this.resourceName});
        }
    }

    public static class PatchUtilException extends RuntimeException {
        private static final long serialVersionUID = 5982003304074821184L;

        public PatchUtilException(String message) {
            super(message);
        }

        public PatchUtilException(Throwable cause) {
            super(cause);
        }

        public PatchUtilException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

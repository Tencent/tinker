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

import com.google.common.base.Joiner;
import com.tencent.tinker.build.aapt.RDotTxtEntry.IdType;
import com.tencent.tinker.build.aapt.RDotTxtEntry.RType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class AaptResourceCollector {

    private final Map<RType, Map<String, Set<ResourceDirectory>>> rTypeResourceDirectoryMap;
    // private final Map<RType, List<ResourceDirectory>> rTypeIncreaseResourceDirectoryListMap;
    // private final Map<RType, Map<ResourceDirectory,ResourceDirectory>> rTypeIncreaseResourceDirectoryMap;
    private final Map<RType, ResourceIdEnumerator>                rTypeEnumeratorMap;
    private final Map<RDotTxtEntry, RDotTxtEntry>                 originalResourceMap;
    private final Map<RType, Set<RDotTxtEntry>>                   rTypeResourceMap;
    private final Map<RType, Set<RDotTxtEntry>>                   rTypeIncreaseResourceMap;
    private final Map<String, Set<String>>                        duplicateResourceMap;
    private final Map<RType, HashMap<String, String>>             sanitizeTypeMap;
    private final Set<String>                                     ignoreIdSet;
    private       int                                             currentTypeId;

    public AaptResourceCollector() {
        this.rTypeResourceDirectoryMap = new HashMap<RType, Map<String, Set<ResourceDirectory>>>();
        // this.rTypeIncreaseResourceDirectoryListMap = new HashMap<RType, List<ResourceDirectory>>();
        // this.rTypeIncreaseResourceDirectoryMap = new HashMap<RType, Map<ResourceDirectory,ResourceDirectory>>();
        this.rTypeEnumeratorMap = new HashMap<RType, ResourceIdEnumerator>();
        this.rTypeResourceMap = new HashMap<RType, Set<RDotTxtEntry>>();
        this.rTypeIncreaseResourceMap = new HashMap<RType, Set<RDotTxtEntry>>();
        this.duplicateResourceMap = new HashMap<String, Set<String>>();
        this.sanitizeTypeMap = new HashMap<RType, HashMap<String, String>>();
        this.originalResourceMap = new HashMap<RDotTxtEntry, RDotTxtEntry>();
        this.ignoreIdSet = new HashSet<String>();
        // attr type must 1
        this.currentTypeId = 2;
    }

    public AaptResourceCollector(Map<RType, Set<RDotTxtEntry>> rTypeResourceMap) {
        this();
        if (rTypeResourceMap != null) {
            Iterator<Entry<RType, Set<RDotTxtEntry>>> iterator = rTypeResourceMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<RType, Set<RDotTxtEntry>> entry = iterator.next();
                RType rType = entry.getKey();
                Set<RDotTxtEntry> set = entry.getValue();
                // this.rTypeResourceMap.put(rType, new HashSet<RDotTxtEntry>(set));
                for (RDotTxtEntry rDotTxtEntry : set) {
                    originalResourceMap.put(rDotTxtEntry, rDotTxtEntry);
                    ResourceIdEnumerator resourceIdEnumerator = null;
                    if (!rDotTxtEntry.idType.equals(IdType.INT_ARRAY)) {
                        int resourceId = Integer.decode(rDotTxtEntry.idValue.trim()).intValue();
                        int typeId = ((resourceId & 0x00FF0000) / 0x00010000);
                        if (typeId >= currentTypeId) {
                            currentTypeId = typeId + 1;
                        }
                        if (this.rTypeEnumeratorMap.containsKey(rType)) {
                            resourceIdEnumerator = this.rTypeEnumeratorMap.get(rType);
                            if (resourceIdEnumerator.currentId < resourceId) {
                                resourceIdEnumerator.currentId = resourceId;
                            }
                        } else {
                            resourceIdEnumerator = new ResourceIdEnumerator();
                            resourceIdEnumerator.currentId = resourceId;
                            this.rTypeEnumeratorMap.put(rType, resourceIdEnumerator);
                        }
                    }
                }
            }
        }
    }

    public void addIntResourceIfNotPresent(RType rType, String name) { //, ResourceDirectory resourceDirectory) {
        if (!rTypeEnumeratorMap.containsKey(rType)) {
            if (rType.equals(RType.ATTR)) {
                rTypeEnumeratorMap.put(rType, new ResourceIdEnumerator(1));
            } else {
                rTypeEnumeratorMap.put(rType, new ResourceIdEnumerator(currentTypeId++));
            }
        }

        RDotTxtEntry entry = new FakeRDotTxtEntry(IdType.INT, rType, name);
        Set<RDotTxtEntry> resourceSet = null;
        if (this.rTypeResourceMap.containsKey(rType)) {
            resourceSet = this.rTypeResourceMap.get(rType);
        } else {
            resourceSet = new HashSet<RDotTxtEntry>();
            this.rTypeResourceMap.put(rType, resourceSet);
        }
        if (!resourceSet.contains(entry)) {
            String idValue = String.format("0x%08x", rTypeEnumeratorMap.get(rType).next());
            addResource(rType, IdType.INT, name, idValue); //, resourceDirectory);
        }
    }

    public void addIntArrayResourceIfNotPresent(RType rType, String name, int numValues) {
        // Robolectric expects the array to be populated with the right number
        // of values, irrespective
        // of what the values are.
        String idValue = String.format("{ %s }", Joiner.on(",").join(Collections.nCopies(numValues, "0x7f000000")));
        addResource(rType, IdType.INT_ARRAY, name, idValue);
    }

    /**
     * add resource
     *
     * @param rType
     * @param idType
     * @param name
     * @param idValue
     */
    public void addResource(RType rType, IdType idType, String name, String idValue) {
        Set<RDotTxtEntry> resourceSet = null;
        if (this.rTypeResourceMap.containsKey(rType)) {
            resourceSet = this.rTypeResourceMap.get(rType);
        } else {
            resourceSet = new HashSet<RDotTxtEntry>();
            this.rTypeResourceMap.put(rType, resourceSet);
        }
        RDotTxtEntry rDotTxtEntry = new RDotTxtEntry(idType, rType, name, idValue);
        boolean increaseResource = false;
        if (!resourceSet.contains(rDotTxtEntry)) {
            if (this.originalResourceMap.containsKey(rDotTxtEntry)) {
                this.rTypeEnumeratorMap.get(rType).previous();
                rDotTxtEntry = this.originalResourceMap.get(rDotTxtEntry);
            } else {
                increaseResource = true;
            }
            resourceSet.add(rDotTxtEntry);
        }
        Set<RDotTxtEntry> increaseResourceSet = null;
        //new r dot txt entry
        if (this.rTypeIncreaseResourceMap.containsKey(rType)) {
            increaseResourceSet = this.rTypeIncreaseResourceMap.get(rType);
        } else {
            increaseResourceSet = new HashSet<RDotTxtEntry>();
            this.rTypeIncreaseResourceMap.put(rType, increaseResourceSet);
        }
        if (increaseResource) {
            increaseResourceSet.add(rDotTxtEntry);
            // addResourceDirectory(rType, name, resourceDirectory);
        }
    }

    // private void addResourceDirectory(RType rType, String name, ResourceDirectory resourceDirectory) {
    //     if (resourceDirectory != null) {
    //         Map<ResourceDirectory, ResourceDirectory> resourceDirectoryMap = null;
    //         List<ResourceDirectory> resourceDirectoryList = null;
    //         if (this.rTypeIncreaseResourceDirectoryMap.containsKey(rType)) {
    //             resourceDirectoryMap = this.rTypeIncreaseResourceDirectoryMap.get(rType);
    //             resourceDirectoryList = this.rTypeIncreaseResourceDirectoryListMap.get(rType);
    //         } else {
    //             resourceDirectoryMap = new HashMap<ResourceDirectory, ResourceDirectory>();
    //             this.rTypeIncreaseResourceDirectoryMap.put(rType, resourceDirectoryMap);
    //             resourceDirectoryList = new ArrayList<ResourceDirectory>();
    //             this.rTypeIncreaseResourceDirectoryListMap.put(rType, resourceDirectoryList);
    //         }
    //         ResourceDirectory existResourceDirectory = null;
    //         if (resourceDirectoryMap.containsKey(resourceDirectory)) {
    //             existResourceDirectory = resourceDirectoryMap.get(resourceDirectory);
    //         } else {
    //             existResourceDirectory = resourceDirectory;
    //             resourceDirectoryMap.put(resourceDirectory, resourceDirectory);
    //             resourceDirectoryList.add(existResourceDirectory);
    //         }
    //         existResourceDirectory.resourceEntrySet.add(new ResourceEntry(name, null));
    //     }
    // }

    /**
     * is contain resource
     *
     * @param rType
     * @param idType
     * @param name
     * @return boolean
     */
    public boolean isContainResource(RType rType, IdType idType, String name) {
        boolean result = false;
        if (this.rTypeResourceMap.containsKey(rType)) {
            Set<RDotTxtEntry> resourceSet = this.rTypeResourceMap.get(rType);
            if (resourceSet.contains(new RDotTxtEntry(idType, rType, name, "0x7f000000"))) {
                result = true;
            }
        }
        return result;
    }

    /**
     * add r type resource name
     *
     * @param rType
     * @param resourceName
     * @param resourceDirectory
     */
    void addRTypeResourceName(RType rType, String resourceName, String resourceValue, ResourceDirectory resourceDirectory) {
        Map<String, Set<ResourceDirectory>> directoryResourceDirectoryMap = null;
        if (this.rTypeResourceDirectoryMap.containsKey(rType)) {
            directoryResourceDirectoryMap = this.rTypeResourceDirectoryMap.get(rType);
        } else {
            directoryResourceDirectoryMap = new HashMap<String, Set<ResourceDirectory>>();
            this.rTypeResourceDirectoryMap.put(rType, directoryResourceDirectoryMap);
        }
        Set<ResourceDirectory> resourceDirectorySet = null;
        if (directoryResourceDirectoryMap.containsKey(resourceDirectory.directoryName)) {
            resourceDirectorySet = directoryResourceDirectoryMap.get(resourceDirectory.directoryName);
        } else {
            resourceDirectorySet = new HashSet<ResourceDirectory>();
            directoryResourceDirectoryMap.put(resourceDirectory.directoryName, resourceDirectorySet);
        }
        boolean find = false;
        ResourceDirectory newResourceDirectory = new ResourceDirectory(resourceDirectory.directoryName, resourceDirectory.resourceFullFilename);
        if (!resourceDirectorySet.contains(newResourceDirectory)) {
            resourceDirectorySet.add(newResourceDirectory);
        }
        for (ResourceDirectory oldResourceDirectory : resourceDirectorySet) {
            if (oldResourceDirectory.resourceEntrySet.contains(new ResourceEntry(resourceName, resourceValue))) {
                find = true;
                String resourceKey = rType + "/" + resourceDirectory.directoryName + "/" + resourceName;
                Set<String> fullFilenameSet = null;
                if (!this.duplicateResourceMap.containsKey(resourceKey)) {
                    fullFilenameSet = new HashSet<String>();
                    fullFilenameSet.add(oldResourceDirectory.resourceFullFilename);
                    this.duplicateResourceMap.put(resourceKey, fullFilenameSet);
                } else {
                    fullFilenameSet = this.duplicateResourceMap.get(resourceKey);
                }
                fullFilenameSet.add(resourceDirectory.resourceFullFilename);
            }
        }
        if (!find) {
            for (ResourceDirectory oldResourceDirectory : resourceDirectorySet) {
                if (oldResourceDirectory.equals(newResourceDirectory)) {
                    if (!oldResourceDirectory.resourceEntrySet.contains(new ResourceEntry(resourceName, resourceValue))) {
                        oldResourceDirectory.resourceEntrySet.add(new ResourceEntry(resourceName, resourceValue));
                    }
                }
            }
        }
    }

    void putSanitizeName(RType rType, String sanitizeName, String rawName) {
        HashMap<String, String> sanitizeNameMap;
        if (!sanitizeTypeMap.containsKey(rType)) {
            sanitizeNameMap = new HashMap<>();
            sanitizeTypeMap.put(rType, sanitizeNameMap);
        } else {
            sanitizeNameMap = sanitizeTypeMap.get(rType);
        }
        if (!sanitizeNameMap.containsKey(sanitizeName)) {
            sanitizeNameMap.put(sanitizeName, rawName);
        }
    }

    /**
     * get raw name
     *
     * @param sanitizeName
     * @return String
     */
    public String getRawName(RType rType, String sanitizeName) {
        if (!sanitizeTypeMap.containsKey(rType)) {
            return null;
        }
        return this.sanitizeTypeMap.get(rType).get(sanitizeName);
    }

    /**
     * get r type resource map
     *
     * @return Map<RType, Set<RDotTxtEntry>>
     */
    public Map<RType, Set<RDotTxtEntry>> getRTypeResourceMap() {
        return this.rTypeResourceMap;
    }

    /**
     * @return the duplicateResourceMap
     */
    public Map<String, Set<String>> getDuplicateResourceMap() {
        return duplicateResourceMap;
    }

    /**
     * @return the rTypeIncreaseResourceMap
     */
    public Map<RType, Set<RDotTxtEntry>> getRTypeIncreaseResourceMap() {
        return rTypeIncreaseResourceMap;
    }

    /**
     * @return the rTypeResourceDirectoryMap
     */
    public Map<RType, Map<String, Set<ResourceDirectory>>> getRTypeResourceDirectoryMap() {
        return rTypeResourceDirectoryMap;
    }

    // /**
    // * @return the rTypeIncreaseResourceDirectoryListMap
    // */
    // public Map<RType, List<ResourceDirectory>> getRTypeIncreaseResourceDirectoryListMap() {
    //     return rTypeIncreaseResourceDirectoryListMap;
    // }

    void addIgnoreId(String name) {
        ignoreIdSet.add(name);
    }

    /**
     * @return the ignoreIdSet
     */
    public Set<String> getIgnoreIdSet() {
        return ignoreIdSet;
    }

    private static class ResourceIdEnumerator {

        private int currentId = 0;

        ResourceIdEnumerator() {
        }

        ResourceIdEnumerator(int typeId) {
            this.currentId = 0x7f000000 + 0x10000 * typeId + -1;
        }

        int previous() {
            return --currentId;
        }

        int next() {
            return ++currentId;
        }
    }
}

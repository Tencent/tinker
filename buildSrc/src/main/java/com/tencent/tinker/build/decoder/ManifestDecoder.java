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

package com.tencent.tinker.build.decoder;


import com.tencent.tinker.build.apkparser.AndroidParser;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.XMLWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tinker.net.dongliu.apk.parser.bean.ApkMeta;
import tinker.net.dongliu.apk.parser.bean.GlEsVersion;
import tinker.net.dongliu.apk.parser.bean.Permission;
import tinker.net.dongliu.apk.parser.bean.UseFeature;

/**
 * Created by zhangshaowen on 16/4/6.
 */

public class ManifestDecoder extends BaseDecoder {
    private static final String XML_NODENAME_APPLICATION        = "application";
    private static final String XML_NODENAME_USES_SDK           = "uses-sdk";
    private static final String XML_NODEATTR_MIN_SDK_VERSION    = "minSdkVersion";
    private static final String XML_NODEATTR_TARGET_SDK_VERSION = "targetSdkVersion";
    private static final String XML_NODEATTR_PACKAGE            = "package";
    private static final String XML_NODENAME_ACTIVITY           = "activity";
    private static final String XML_NODENAME_SERVICE            = "service";
    private static final String XML_NODENAME_RECEIVER           = "receiver";
    private static final String XML_NODENAME_PROVIDER           = "provider";
    private static final String XML_NODEATTR_NAME               = "name";
    private static final String XML_NODEATTR_EXPORTED           = "exported";
    private static final String XML_NODEATTR_PROCESS            = "process";
    private static final String XML_NODENAME_INTENTFILTER       = "intent-filter";

    public ManifestDecoder(Configuration config) throws IOException {
        super(config);
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        try {
            AndroidParser oldAndroidManifest = AndroidParser.getAndroidManifest(oldFile);
            AndroidParser newAndroidManifest = AndroidParser.getAndroidManifest(newFile);

            //check minSdkVersion
            int minSdkVersion = Integer.parseInt(oldAndroidManifest.apkMeta.getMinSdkVersion());

            if (minSdkVersion < TypedValue.ANDROID_40_API_LEVEL) {
                if (config.mDexRaw) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("your old apk's minSdkVersion ")
                      .append(minSdkVersion)
                      .append(" is below 14, you should set the dexMode to 'jar', ")
                      .append("otherwise, it will crash at some time");
                    announceWarningOrException(sb.toString());
                }
            }

            final String oldXml = oldAndroidManifest.xml.trim();
            final String newXml = newAndroidManifest.xml.trim();
            final boolean isManifestChanged = !oldXml.equals(newXml);

            if (!isManifestChanged) {
                Logger.d("\nManifest has no changes, skip rest decode works.");
                return false;
            }

            ensureApkMetaUnchanged(oldAndroidManifest.apkMeta, newAndroidManifest.apkMeta);

            // check whether there is any new Android Component and get their names.
            // so far only Activity increment can pass checking.
            final Set<String> incActivities = getIncrementActivities(oldAndroidManifest.activities, newAndroidManifest.activities);
            final Set<String> incServices = getIncrementServices(oldAndroidManifest.services, newAndroidManifest.services);
            final Set<String> incReceivers = getIncrementReceivers(oldAndroidManifest.receivers, newAndroidManifest.receivers);
            final Set<String> incProviders = getIncrementProviders(oldAndroidManifest.providers, newAndroidManifest.providers);

            final boolean hasIncComponent = (!incActivities.isEmpty() || !incServices.isEmpty()
                    || !incProviders.isEmpty() || !incReceivers.isEmpty());

            if (!config.mSupportHotplugComponent && hasIncComponent) {
                announceWarningOrException("manifest was changed, while hot plug component support mode is disabled. "
                        + "Such changes will not take effect, related components: \n"
                        + " activity: " + incActivities + "\n"
                        + " service: " + incServices + "\n"
                        + " receiver: " + incReceivers + "\n"
                        + " provider: " + incProviders + "\n"
                );
            }

            // generate increment manifest.
            if (hasIncComponent) {
                final Document newXmlDoc = DocumentHelper.parseText(newAndroidManifest.xml);
                final Document incXmlDoc = DocumentHelper.createDocument();

                final Element newRootNode = newXmlDoc.getRootElement();
                final String packageName = newRootNode.attributeValue(XML_NODEATTR_PACKAGE);
                if (Utils.isNullOrNil(packageName)) {
                    throw new TinkerPatchException("Unable to find package name from manifest: " + newFile.getAbsolutePath());
                }

                final Element newAppNode = newRootNode.element(XML_NODENAME_APPLICATION);

                final Element incAppNode = incXmlDoc.addElement(newAppNode.getQName());
                copyAttributes(newAppNode, incAppNode);

                if (!incActivities.isEmpty()) {
                    final List<Element> newActivityNodes = newAppNode.elements(XML_NODENAME_ACTIVITY);
                    final List<Element> incActivityNodes = getIncrementActivityNodes(packageName, newActivityNodes, incActivities);
                    for (Element node : incActivityNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                if (!incServices.isEmpty()) {
                    final List<Element> newServiceNodes = newAppNode.elements(XML_NODENAME_SERVICE);
                    final List<Element> incServiceNodes = getIncrementServiceNodes(packageName, newServiceNodes, incServices);
                    for (Element node : incServiceNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                if (!incReceivers.isEmpty()) {
                    final List<Element> newReceiverNodes = newAppNode.elements(XML_NODENAME_RECEIVER);
                    final List<Element> incReceiverNodes = getIncrementReceiverNodes(packageName, newReceiverNodes, incReceivers);
                    for (Element node : incReceiverNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                if (!incProviders.isEmpty()) {
                    final List<Element> newProviderNodes = newAppNode.elements(XML_NODENAME_PROVIDER);
                    final List<Element> incProviderNodes = getIncrementProviderNodes(packageName, newProviderNodes, incProviders);
                    for (Element node : incProviderNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                final File incXmlOutput = new File(config.mTempResultDir, TypedValue.INCCOMPONENT_META_FILE);
                if (!incXmlOutput.exists()) {
                    incXmlOutput.getParentFile().mkdirs();
                }
                OutputStream os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(incXmlOutput));
                    final XMLWriter docWriter = new XMLWriter(os);
                    docWriter.write(incXmlDoc);
                    docWriter.close();
                } finally {
                    Utils.closeQuietly(os);
                }
            }

            if (isManifestChanged && !hasIncComponent) {
                Logger.d("\nManifest was changed, while there's no any new components added."
                       + " Make sure if such changes were all you expected.\n");
            }

        } catch (ParseException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Parse android manifest error!");
        } catch (DocumentException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Parse android manifest by dom4j error!");
        } catch (IOException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Failed to generate increment manifest.", e);
        }

        return false;
    }

    private void ensureApkMetaUnchanged(ApkMeta oldMeta, ApkMeta newMeta) {
        if (oldMeta == null && newMeta == null) {
            // Impossible situation, for edge case protection only.
            return;
        }
        if (oldMeta != null && newMeta != null) {
            if (!nullSafeEquals(oldMeta.getPackageName(), newMeta.getPackageName(), null)) {
                announceWarningOrException("Package name changed, old: " + oldMeta.getPackageName()
                        + ", new: " + newMeta.getPackageName());
            }
            if (!nullSafeEquals(oldMeta.getLabel(), newMeta.getLabel(), null)) {
                announceWarningOrException("App label changed, old: " + oldMeta.getLabel()
                        + ", new: " + newMeta.getLabel());
            }
            if (!nullSafeEquals(oldMeta.getIcon(), newMeta.getIcon(), null)) {
                announceWarningOrException("App icon res ref changed, old: " + oldMeta.getIcon()
                        + ", new: " + newMeta.getIcon());
            }
            if (!nullSafeEquals(oldMeta.getVersionName(), newMeta.getVersionName(), null)) {
                Logger.e("Note: Version name changed, old: " + oldMeta.getVersionName()
                        + ", new: " + newMeta.getVersionName());
            }
            final Long oldVersionCode = oldMeta.getVersionCode();
            final Long newVersionCode = newMeta.getVersionCode();
            if (oldVersionCode != null && newVersionCode != null) {
                if (newVersionCode < oldVersionCode) {
                    announceWarningOrException("Version code downgrade, old: " + oldVersionCode
                            + ", new: " + newVersionCode);
                }
            } else if (!(oldVersionCode == null && newVersionCode == null)) {
                announceWarningOrException("Version code of old or new apk is missing, old: " + oldVersionCode
                        + ", new: " + newVersionCode);
            }
            if (!nullSafeEquals(oldMeta.getInstallLocation(), newMeta.getInstallLocation(), null)) {
                announceWarningOrException("Install location changed, old: " + oldMeta.getInstallLocation()
                        + ", new: " + newMeta.getInstallLocation());
            }
            if (!nullSafeEquals(oldMeta.getMinSdkVersion(), newMeta.getMinSdkVersion(), null)) {
                announceWarningOrException("MinSdkVersion changed, old: " + oldMeta.getMinSdkVersion()
                        + ", new: " + newMeta.getMinSdkVersion());
            }
            if (!nullSafeEquals(oldMeta.getTargetSdkVersion(), newMeta.getTargetSdkVersion(), null)) {
                announceWarningOrException("TargetSdkVersion changed, old: " + oldMeta.getTargetSdkVersion()
                        + ", new: " + newMeta.getTargetSdkVersion());
            }
            if (!nullSafeEquals(oldMeta.getMaxSdkVersion(), newMeta.getMaxSdkVersion(), null)) {
                announceWarningOrException("MaxSdkVersion changed, old: " + oldMeta.getMaxSdkVersion()
                        + ", new: " + newMeta.getMaxSdkVersion());
            }
            if (!nullSafeEquals(oldMeta.getGlEsVersion(), newMeta.getGlEsVersion(), GLES_VERSION_EQUALS)) {
                announceWarningOrException("GLEsVersion changed, old: "
                        + GLES_VERSION_DESCRIBER.describe(oldMeta.getGlEsVersion())
                        + ", new: " + GLES_VERSION_DESCRIBER.describe(newMeta.getGlEsVersion()));
            }
            if (!nullSafeEquals(oldMeta.isAnyDensity(), newMeta.isAnyDensity(), null)) {
                announceWarningOrException("Value of isAnyDensity changed, old: " + oldMeta.isAnyDensity()
                        + ", new: " + newMeta.isAnyDensity());
            }
            if (!nullSafeEquals(oldMeta.isSmallScreens(), newMeta.isSmallScreens(), null)) {
                announceWarningOrException("Value of isSmallScreens changed, old: " + oldMeta.isSmallScreens()
                        + ", new: " + newMeta.isSmallScreens());
            }
            if (!nullSafeEquals(oldMeta.isNormalScreens(), newMeta.isNormalScreens(), null)) {
                announceWarningOrException("Value of isNormalScreens changed, old: " + oldMeta.isNormalScreens()
                        + ", new: " + newMeta.isNormalScreens());
            }
            if (!nullSafeEquals(oldMeta.isLargeScreens(), newMeta.isLargeScreens(), null)) {
                announceWarningOrException("Value of isLargeScreens changed, old: " + oldMeta.isLargeScreens()
                        + ", new: " + newMeta.isLargeScreens());
            }
            if (!nullSafeEqualsIgnoreOrder(oldMeta.getUsesPermissions(), newMeta.getUsesPermissions(), null)) {
                announceWarningOrException("Uses permissions changed, related uses-permissions: "
                        + describeChanges(oldMeta.getUsesPermissions(), newMeta.getUsesPermissions()));
            }
            if (!nullSafeEqualsIgnoreOrder(oldMeta.getUsesFeatures(), newMeta.getUsesFeatures(), USE_FEATURE_DESCRIBER)) {
                announceWarningOrException("Uses features changed, related uses-features: "
                        + describeChanges(oldMeta.getUsesFeatures(), newMeta.getUsesFeatures(), USE_FEATURE_DESCRIBER));
            }
            if (!nullSafeEqualsIgnoreOrder(oldMeta.getPermissions(), newMeta.getPermissions(), PERMISSION_DESCRIBER)) {
                announceWarningOrException("Uses features changed, related permissions: "
                        + describeChanges(oldMeta.getPermissions(), newMeta.getPermissions(), PERMISSION_DESCRIBER));
            }
        } else {
            announceWarningOrException("One of apk meta is null, are we processing invalid manifest ?");
        }
    }

    private interface EqualsChecker<T> {
        boolean isEquals(T lhs, T rhs);
    }

    private static final EqualsChecker<GlEsVersion> GLES_VERSION_EQUALS = new EqualsChecker<GlEsVersion>() {
        @Override
        public boolean isEquals(GlEsVersion lhs, GlEsVersion rhs) {
            if (lhs.getMajor() != rhs.getMajor()) {
                return false;
            }
            if (lhs.getMinor() != rhs.getMinor()) {
                return false;
            }
            return lhs.isRequired() == rhs.isRequired();
        }
    };

    private static <T> boolean nullSafeEquals(T lhs, T rhs, EqualsChecker<T> equalsChecker) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs != null && rhs != null) {
            return (equalsChecker != null ? equalsChecker.isEquals(lhs, rhs) : lhs.equals(rhs));
        }
        return false;
    }

    private static <T> boolean nullSafeEqualsIgnoreOrder(List<T> lhs, List<T> rhs, ObjectDescriber<T> describer) {
        if (lhs == null && rhs == null) {
            return true;
        }
        if (lhs != null && rhs != null) {
            final Set<String> lhsDescs = new HashSet<>();
            int lhsNotNullElemCount = 0;
            int rhsNotNullElemCount = 0;
            for (int i = 0; i < lhs.size(); ++i) {
                final T lhsElem = lhs.get(i);
                if (lhsElem == null) {
                    continue;
                }
                lhsDescs.add(describer != null ? describer.describe(lhsElem) : lhsElem.toString());
                ++lhsNotNullElemCount;
            }
            boolean hasAddedElemDesc = false;
            for (int i = 0; i < rhs.size(); ++i) {
                final T rhsElem = rhs.get(i);
                if (rhsElem == null) {
                    continue;
                }
                final String rhsElemDesc = describer != null ? describer.describe(rhsElem) : rhsElem.toString();
                if (!lhsDescs.remove(rhsElemDesc)) {
                    hasAddedElemDesc = true;
                    break;
                }
                ++rhsNotNullElemCount;
            }
            if (hasAddedElemDesc) {
                return false;
            }
            if (lhsDescs.size() > 0) {
                // Has removed items.
                return false;
            }
            return lhsNotNullElemCount == rhsNotNullElemCount;
        }
        return false;
    }

    private interface ObjectDescriber<T> {
        String describe(T obj);
    }

    private static final ObjectDescriber<GlEsVersion> GLES_VERSION_DESCRIBER = new ObjectDescriber<GlEsVersion>() {
        @Override
        public String describe(GlEsVersion obj) {
            final StringBuilder sb = new StringBuilder();
            sb.append("{")
              .append("majar:").append(obj.getMajor())
              .append("minor:").append(obj.getMinor())
              .append(",required:").append(obj.isRequired())
              .append("}");
            return sb.toString();
        }
    };

    private static final ObjectDescriber<UseFeature> USE_FEATURE_DESCRIBER = new ObjectDescriber<UseFeature>() {
        @Override
        public String describe(UseFeature obj) {
            final StringBuilder sb = new StringBuilder();
            sb.append("{")
              .append("name:").append(obj.getName())
              .append(",required:").append(obj.isRequired())
              .append("}");
            return sb.toString();
        }
    };

    private static final ObjectDescriber<Permission> PERMISSION_DESCRIBER = new ObjectDescriber<Permission>() {
        @Override
        public String describe(Permission obj) {
            final StringBuilder sb = new StringBuilder();
            sb.append("{")
              .append("name:").append(obj.getName())
              .append(",label:").append(obj.getLabel())
              .append(",icon:").append(obj.getIcon())
              .append(",description:").append(obj.getDescription())
              .append(",group:").append(obj.getGroup())
              .append(",protectionLevel:").append(obj.getProtectionLevel())
              .append("}");
            return sb.toString();
        }
    };

    private static <T> String describeChanges(Collection<T> oldObjs, Collection<T> newObjs) {
        return describeChanges(oldObjs, newObjs, null);
    }

    private static <T> String describeChanges(Collection<T> oldObjs, Collection<T> newObjs, ObjectDescriber<T> describer) {
        final Set<String> oldDescs = new HashSet<>();
        final List<String> addedDescs = new ArrayList<>();
        for (T oldObj : oldObjs) {
            oldDescs.add(describer != null ? describer.describe(oldObj) : oldObj.toString());
        }
        for (T newObj : newObjs) {
            final String newDesc = describer != null ? describer.describe(newObj) : newObj.toString();
            if (!oldDescs.remove(newDesc)) {
                addedDescs.add(newDesc);
            }
        }
        final List<String> removedDescs = new ArrayList<>(oldDescs);
        final StringBuilder sb = new StringBuilder();
        sb.append("{added:").append(addedDescs)
          .append(",removed:").append(removedDescs)
          .append("}");
        return sb.toString();
    }

    private Set<String> getIncrementActivities(Collection<String> oldActivities, Collection<String> newActivities) {
        final Set<String> incNames = new HashSet<>(newActivities);
        incNames.removeAll(oldActivities);
        return incNames;
    }

    private Set<String> getIncrementServices(Collection<String> oldServices, Collection<String> newServices) {
        final Set<String> incNames = new HashSet<>(newServices);
        incNames.removeAll(oldServices);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added services: " + incNames.toString()
                    + "\n currently tinker does not support increase new services, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private Set<String> getIncrementReceivers(Collection<String> oldReceivers, Collection<String> newReceivers) {
        final Set<String> incNames = new HashSet<>(newReceivers);
        incNames.removeAll(oldReceivers);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added receivers: " + incNames.toString()
                    + "\n currently tinker does not support increase new receivers, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private Set<String> getIncrementProviders(Collection<String> oldProviders, Collection<String> newProviders) {
        final Set<String> incNames = new HashSet<>(newProviders);
        incNames.removeAll(oldProviders);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added providers: " + incNames.toString()
                    + "\n currently tinker does not support increase new providers, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private List<Element> getIncrementActivityNodes(String packageName, List<Element> newActivityNodes, Collection<String> incActivities) {
        final List<Element> result = new ArrayList<>();
        for (Element newActivityNode : newActivityNodes) {
            String activityClazzName = newActivityNode.attributeValue(XML_NODEATTR_NAME);
            if (activityClazzName.charAt(0) == '.') {
                activityClazzName = packageName + activityClazzName;
            }
            if (!incActivities.contains(activityClazzName)) {
                continue;
            }
            final String exportedVal = newActivityNode.attributeValue(XML_NODEATTR_EXPORTED,
                    Utils.isNullOrNil(newActivityNode.elements(XML_NODENAME_INTENTFILTER)) ? "false" : "true");
            if ("true".equalsIgnoreCase(exportedVal)) {
                announceWarningOrException(
                        String.format("found a new exported activity %s"
                                + ", tinker does not support increase exported activity.", activityClazzName)
                );
            }
            final String processVal = newActivityNode.attributeValue(XML_NODEATTR_PROCESS);
            if (processVal != null && processVal.charAt(0) == ':') {
                announceWarningOrException(
                        String.format("found a new activity %s which would be run in standalone process"
                                + ", tinker does not support increase such kind of activities.", activityClazzName)
                );
            }

            Logger.d("Found increment activity: " + activityClazzName);

            result.add(newActivityNode);
        }
        return result;
    }

    private List<Element> getIncrementServiceNodes(String packageName, List<Element> newServiceNodes, Collection<String> incServices) {
        announceWarningOrException("currently tinker does not support increase new services.");
        return Collections.emptyList();
    }

    private List<Element> getIncrementReceiverNodes(String packageName, List<Element> newReceiverNodes, Collection<String> incReceivers) {
        announceWarningOrException("currently tinker does not support increase new receivers.");
        return Collections.emptyList();
    }

    private List<Element> getIncrementProviderNodes(String packageName, List<Element> newProviderNodes, Collection<String> incProviders) {
        announceWarningOrException("currently tinker does not support increase new providers.");
        return Collections.emptyList();
    }

    private void copyAttributes(Element srcNode, Element destNode) {
        for (Object attrObj : srcNode.attributes()) {
            final Attribute attr = (Attribute) attrObj;
            destNode.addAttribute(attr.getQName(), attr.getValue());
        }
    }

    private void announceWarningOrException(String message) {
        if (config.mIgnoreWarning) {
            final String msg = "Warning:ignoreWarning is true, but " + message;
            Logger.e(msg);
        } else {
            final String msg = "Warning:ignoreWarning is false, " + message;
            Logger.e(msg);
            throw new TinkerPatchException(msg);
        }
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }
}

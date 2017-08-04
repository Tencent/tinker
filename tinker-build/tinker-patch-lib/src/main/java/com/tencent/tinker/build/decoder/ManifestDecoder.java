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
import java.util.List;

/**
 * Created by zhangshaowen on 16/4/6.
 */

public class ManifestDecoder extends BaseDecoder {
    private static final String XML_NODENAME_APPLICATION  = "application";
    private static final String XML_NODEATTR_PACKAGE      = "package";
    private static final String XML_NODENAME_ACTIVITY     = "activity";
    private static final String XML_NODEATTR_NAME         = "name";
    private static final String XML_NODEATTR_EXPORTED     = "exported";
    private static final String XML_NODEATTR_PROCESS      = "process";
    private static final String XML_NODENAME_INTENTFILTER = "intent-filter";

    public ManifestDecoder(Configuration config) throws IOException {
        super(config);
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        final boolean ignoreWarning = config.mIgnoreWarning;

        final List<String> addedActivities = new ArrayList<>();

        try {
            AndroidParser oldAndroidManifest = AndroidParser.getAndroidManifest(oldFile);
            AndroidParser newAndroidManifest = AndroidParser.getAndroidManifest(newFile);
            //check minSdkVersion
            int minSdkVersion = Integer.parseInt(oldAndroidManifest.apkMeta.getMinSdkVersion());

            if (minSdkVersion < TypedValue.ANDROID_40_API_LEVEL) {
                if (config.mDexRaw) {
                    if (ignoreWarning) {
                        //ignoreWarning, just log
                        Logger.e("Warning:ignoreWarning is true, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion);
                    } else {
                        Logger.e("Warning:ignoreWarning is false, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion);

                        throw new TinkerPatchException(
                            String.format("ignoreWarning is false, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion)
                        );
                    }
                }
            }

            //check whether there is any new Android Component
            List<String> oldAndroidComponent = oldAndroidManifest.getComponents();
            List<String> newAndroidComponent = newAndroidManifest.getComponents();

            for (String newComponentName : newAndroidComponent) {
                boolean found = false;
                for (String oldComponentName : oldAndroidComponent) {
                    if (newComponentName.equals(oldComponentName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Currently only activities are supported.
                    if (newAndroidManifest.activities.contains(newComponentName)) {
                        addedActivities.add(newComponentName);
                    } else {
                        if (ignoreWarning) {
                            Logger.e("Warning:ignoreWarning is true, but we found a new AndroidComponent %s, it will crash at some time", newComponentName);
                        } else {
                            Logger.e("Warning:ignoreWarning is false, but we found a new AndroidComponent %s, it will crash at some time", newComponentName);
                            throw new TinkerPatchException(
                                    String.format("ignoreWarning is false, but we found a new AndroidComponent %s, it will crash at some time", newComponentName)
                            );
                        }
                    }
                }
            }

            // Checking works are done, then we store added activities' information into a standalone xml file.
            if (!addedActivities.isEmpty()) {
                final Document incXmlDoc = DocumentHelper.createDocument();
                final Document xmlDoc = DocumentHelper.parseText(newAndroidManifest.xml);
                final Element rootElement = xmlDoc.getRootElement();
                final String packageName = rootElement.attributeValue(XML_NODEATTR_PACKAGE);
                if (Utils.isNullOrNil(packageName)) {
                    throw new IOException("Unable to find package name from manifest: " + newFile.getAbsolutePath());
                }
                final Element incRootElement = DocumentHelper.createElement(rootElement.getName());
                final Element appElement = rootElement.element(XML_NODENAME_APPLICATION);
                if (appElement == null) {
                    throw new TinkerPatchException("Unable to find application node from manifest: " + newFile.getAbsolutePath());
                }
                final Element incAppElement = DocumentHelper.createElement(XML_NODENAME_APPLICATION);
                for (Object attrObj : appElement.attributes()) {
                    final Attribute attr = (Attribute) attrObj;
                    incAppElement.addAttribute(attr.getName(), attr.getValue());
                }
                incRootElement.add(incAppElement);
                final List<Element> activityElements = appElement.elements(XML_NODENAME_ACTIVITY);
                for (Element activityElement : activityElements) {
                    String activityClazzName = activityElement.attributeValue(XML_NODEATTR_NAME);
                    if (activityClazzName.charAt(0) == '.') {
                        activityClazzName = packageName + activityClazzName;
                    }
                    if (!addedActivities.contains(activityClazzName)) {
                        continue;
                    }
                    final String exportedVal = activityElement.attributeValue(XML_NODEATTR_EXPORTED,
                            Utils.isNullOrNil(activityElement.elements(XML_NODENAME_INTENTFILTER)) ? "false" : "true");
                    if ("true".equalsIgnoreCase(exportedVal)) {
                        throw new TinkerPatchException(
                                    String.format("Found a new exported activity %s"
                                            + ", tinker does not support increase exported activity.", activityClazzName)
                        );
                    }
                    final String processVal = activityElement.attributeValue(XML_NODEATTR_PROCESS);
                    if (processVal != null && processVal.charAt(0) == ':') {
                        throw new TinkerPatchException(
                                String.format("Found a new activity %s which would be run in standalone process"
                                        + ", tinker does not support increase such kind of activities.", activityClazzName)
                        );
                    }
                    incAppElement.add(activityElement.detach());
                }
                incXmlDoc.add(incRootElement);

                final File incXmlOutput = new File(config.mTempResultDir,
                        TypedValue.SO_META_FILE + File.separator + TypedValue.INCCOMPONENT_META_FILE);
                if (!incXmlOutput.exists()) {
                    incXmlOutput.getParentFile().mkdirs();
                }
                OutputStream os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(incXmlOutput));
                    final XMLWriter docWriter = new XMLWriter(os);
                    docWriter.write(incXmlDoc);
                    docWriter.close();
                } catch (IOException e) {
                    throw new TinkerPatchException("Failed to generate increment manifest.", e);
                } finally {
                    Utils.closeQuietly(os);
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
            throw new TinkerPatchException("parse android manifest error!");
        } catch (DocumentException e) {
            e.printStackTrace();
            throw new TinkerPatchException("parse android manifest by dom4j error!");
        }

        return false;
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }
}

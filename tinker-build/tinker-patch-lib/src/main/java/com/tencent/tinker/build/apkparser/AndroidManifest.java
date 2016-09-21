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

package com.tencent.tinker.build.apkparser;

import net.dongliu.apk.parser.ApkParser;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.exception.ParserException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by zhangshaowen on 16/5/5.
 */
public class AndroidManifest {
    public static final int TYPE_SERVICE            = 1;
    public static final int TYPE_ACTIVITY           = 2;
    public static final int TYPE_BROADCAST_RECEIVER = 3;
    public static final int TYPE_CONTENT_PROVIDER   = 4;

    public final List<String> activities = new ArrayList<>();
    public final List<String> receivers  = new ArrayList<>();
    public final List<String> services   = new ArrayList<>();
    public final List<String> providers  = new ArrayList<>();
    public final ApkMeta apkMeta;
    public final String  xml;

    public final HashMap<String, String> metaDatas = new HashMap<>();


    public AndroidManifest(ApkMeta apkMeta, String xml) throws ParserException {
        this.apkMeta = apkMeta;
        this.xml = xml;
        parse();
    }

    public static AndroidManifest getAndroidManifest(File file) throws IOException, ParseException {
        ApkParser apkParser = new ApkParser(file);
        AndroidManifest androidManifest = new AndroidManifest(apkParser.getApkMeta(), apkParser.getManifestXml());
        return androidManifest;
    }

    private static String getAttribute(NamedNodeMap namedNodeMap, String name) {
        Node node = namedNodeMap.getNamedItem(name);
        if (node == null) {
            if (name.startsWith("android:")) {
                name = name.substring("android:".length());
            }
            node = namedNodeMap.getNamedItem(name);
            if (node == null) {
                return null;
            }
        }
        return node.getNodeValue();
    }

    /**
     * @return a list of all components
     */
    public List<String> getComponents() {
        List<String> components = new ArrayList<>();
        components.addAll(activities);
        components.addAll(services);
        components.addAll(receivers);
        components.addAll(providers);
        return components;
    }

    private void parse() throws ParserException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        Document document;
        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            Node manifestNode = document.getElementsByTagName("manifest").item(0);
            NodeList nodes = manifestNode.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String nodeName = node.getNodeName();
                if (nodeName.equals("application")) {
                    NodeList children = node.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        Node child = children.item(j);
                        String childName = child.getNodeName();
                        switch (childName) {
                            case "service":
                                services.add(getAndroidComponent(child, TYPE_SERVICE));
                                break;
                            case "activity":
                                activities.add(getAndroidComponent(child, TYPE_ACTIVITY));
                                break;
                            case "receiver":
                                receivers.add(getAndroidComponent(child, TYPE_BROADCAST_RECEIVER));
                                break;
                            case "provider":
                                providers.add(getAndroidComponent(child, TYPE_CONTENT_PROVIDER));
                                break;
                            case "meta-data":
                                NamedNodeMap attributes = child.getAttributes();
                                metaDatas.put(getAttribute(attributes, "android:name"), getAttribute(attributes, "android:value"));
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ParserException("Error parsing AndroidManifest.xml", e);
        }
    }

    private String getAndroidComponent(Node node, int type) {
        NamedNodeMap attributes = node.getAttributes();
        return getAttribute(attributes, "android:name");
    }
}

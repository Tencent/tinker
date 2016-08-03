/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.build.patch;

import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @author zhangshaowen
 *         do not use Logger here
 */
public class Configuration {

    protected static final String TAG_ISSUE           = "issue";
    protected static final String DEX_ISSUE           = "dex";
    protected static final String SO_ISSUE            = "lib";
    protected static final String SIGN_ISSUE          = "sign";
    protected static final String PACKAGECONFIG_ISSUE = "packageConfig";
    protected static final String PROPERTY_ISSUE      = "property";

    protected static final String ATTR_ID    = "id";
    protected static final String ATTR_VALUE = "value";
    protected static final String ATTR_NAME  = "name";

    protected static final String ATTR_IGNORE_WARNING = "ignoreWarning";
    protected static final String ATTR_USE_SIGN       = "useSign";
    protected static final String ATTR_SEVEN_ZIP_PATH = "sevenZipPath";
    protected static final String ATTR_DEXMODE        = "dexMode";
    protected static final String ATTR_PATTERN        = "pattern";
    protected static final String ATTR_LOADER         = "loader";
    protected static final String ATTR_CONFIG_FIELD   = "configField";


    protected static final String ATTR_SIGNFILE_PATH      = "path";
    protected static final String ATTR_SIGNFILE_KEYPASS   = "keypass";
    protected static final String ATTR_SIGNFILE_STOREPASS = "storepass";
    protected static final String ATTR_SIGNFILE_ALIAS     = "alias";
    /**
     * base config data
     */
    public String                  mOldApkPath;
    public String                  mNewApkPath;
    public String                  mOutFolder;
    public File                    mOldApkFile;
    public File                    mNewApkFile;
    public boolean                 mIgnoreWarning;
    /**
     * lib config
     */
    public HashSet<Pattern>        mSoFilePattern;
    /**
     * dex config
     */
    public HashSet<Pattern>        mDexFilePattern;
    public HashSet<String>         mDexLoaderPattern;
    public boolean                 mDexRaw;
    /**
     * package file config
     */
    public HashMap<String, String> mPackageFileds;
    /**
     * sevenZip path config
     */
    public String                  mSevenZipPath;
    /**
     * sign data
     */
    public boolean                 mUseSignAPk;
    public File                    mSignatureFile;
    public String                  mKeyPass;
    public String                  mStoreAlias;
    public String                  mStorePass;

    /**
     * temp files
     */
    public File mTempResultDir;
    public File mTempUnzipOldDir;
    public File mTempUnzipNewDir;

    /**
     * use by command line with xml config
     */
    public Configuration(File config, File outputFile, File oldApkFile, File newApkFile)
        throws IOException, ParserConfigurationException, SAXException, TinkerPatchException {
        mSoFilePattern = new HashSet<>();
        mDexFilePattern = new HashSet<>();
        mDexLoaderPattern = new HashSet<>();
        mPackageFileds = new HashMap<>();

        mOutFolder = outputFile.getAbsolutePath();
        FileOperation.cleanDir(outputFile);

        mOldApkFile = oldApkFile;
        mOldApkPath = oldApkFile.getAbsolutePath();

        mNewApkFile = newApkFile;
        mNewApkPath = newApkFile.getAbsolutePath();

        readXmlConfig(config);

        createTempDirectory();
        checkInputDexAndSo();
    }


    /**
     * use by gradle
     */
    public Configuration(InputParam param) throws IOException, TinkerPatchException {
        mSoFilePattern = new HashSet<>();
        mDexFilePattern = new HashSet<>();
        mDexLoaderPattern = new HashSet<>();
        mPackageFileds = new HashMap<>();

        for (String item : param.soFilePattern) {
            addToPatterns(item, mSoFilePattern);
        }

        for (String item : param.dexFilePattern) {
            addToPatterns(item, mDexFilePattern);
        }

        mDexLoaderPattern.addAll(param.dexLoaderPattern);

        //can be only raw or jar
        if (param.dexMode.equals("raw")) {
            mDexRaw = true;
        }

        mOldApkPath = param.oldApk;
        mOldApkFile = new File(mOldApkPath);

        mNewApkPath = param.newApk;
        mNewApkFile = new File(mNewApkPath);

        mOutFolder = param.outFolder;

        mIgnoreWarning = param.ignoreWarning;
        mSevenZipPath = param.sevenZipPath;
        mPackageFileds = param.configFields;

        mUseSignAPk = param.useSign;
        setSignData(param.signFile, param.keypass, param.storealias, param.storepass);

        FileOperation.cleanDir(new File(mOutFolder));

        createTempDirectory();
        checkInputDexAndSo();

    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("configuration: \n");
        sb.append("oldApk:" + mOldApkPath + "\n");
        sb.append("newApk:" + mNewApkPath + "\n");
        sb.append("outputFolder:" + mOutFolder + "\n");
        sb.append("isIgnoreWarning:" + mIgnoreWarning + "\n");
        sb.append("7-ZipPath:" + mSevenZipPath + "\n");
        sb.append("useSignAPk:" + mUseSignAPk + "\n");

        sb.append("package meta fields: \n");

        for (String name : mPackageFileds.keySet()) {
            sb.append("filed name:" + name + ", filed value:" + mPackageFileds.get(name) + "\n");
        }

        sb.append("dex configs: \n");
        if (mDexRaw) {
            sb.append("dexMode: raw" + "\n");
        } else {
            sb.append("dexMode: jar" + "\n");
        }
        for (Pattern name : mDexFilePattern) {
            sb.append("dexPattern:" + name.toString() + "\n");
        }
        for (String name : mDexLoaderPattern) {
            sb.append("dex loader:" + name + "\n");
        }

        sb.append("lib configs: \n");
        for (Pattern name : mSoFilePattern) {
            sb.append("libPattern:" + name.toString() + "\n");
        }
        return sb.toString();
    }

    private void createTempDirectory() throws TinkerPatchException {
        mTempResultDir = new File(mOutFolder + File.separator + TypedValue.PATH_PATCH_FILES);
        if (!mTempResultDir.exists()) {
            mTempResultDir.mkdir();
        }

        String oldApkName = mOldApkFile.getName();
        if (!oldApkName.endsWith(TypedValue.FILE_APK)) {
            throw new TinkerPatchException(
                String.format("input apk file path must end with .apk, yours %s\n", oldApkName)
            );
        }

        String newApkName = mNewApkFile.getName();
        if (!newApkName.endsWith(TypedValue.FILE_APK)) {
            throw new TinkerPatchException(
                String.format("input apk file path must end with .apk, yours %s\n", newApkName)
            );
        }

        String tempOldName = oldApkName.substring(0, oldApkName.indexOf(TypedValue.FILE_APK));


        String tempNewName = newApkName.substring(0, newApkName.indexOf(TypedValue.FILE_APK));

        if (tempNewName.equals(tempOldName)) {
            tempOldName += "-old";
            tempNewName += "-new";
        }

        mTempUnzipOldDir = new File(mOutFolder, tempOldName);
        mTempUnzipNewDir = new File(mOutFolder, tempNewName);
    }

    public void setSignData(File signatureFile, String keypass, String storealias, String storepass) throws IOException {
        if (mUseSignAPk) {
            mSignatureFile = signatureFile;
            if (!mSignatureFile.exists()) {
                throw new IOException(
                    String.format("the signature file do not exit, raw path= %s\n", mSignatureFile.getAbsolutePath())
                );
            }
            mKeyPass = keypass;
            mStoreAlias = storealias;
            mStorePass = storepass;
        }
    }

    private void checkInputDexAndSo() throws TinkerPatchException {
        if (mSoFilePattern.isEmpty() && mDexFilePattern.isEmpty()) {
            throw new TinkerPatchException("no dex or so pattern are found");
        }

    }

    /**
     * read args from xml
     **/
    void readXmlConfig(File xmlConfigFile)
        throws IOException, ParserConfigurationException, SAXException {
        if (!xmlConfigFile.exists()) {
            return;
        }

        System.out.printf("reading config file, %s\n", xmlConfigFile.getAbsolutePath());
        BufferedInputStream input = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            input = new BufferedInputStream(new FileInputStream(xmlConfigFile));
            InputSource source = new InputSource(input);
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(source);
            NodeList issues = document.getElementsByTagName(TAG_ISSUE);
            for (int i = 0, count = issues.getLength(); i < count; i++) {
                Node node = issues.item(i);

                Element element = (Element) node;
                String id = element.getAttribute(ATTR_ID);
                if (id.length() == 0) {
                    System.err.println("Invalid config file: Missing required issue id attribute");
                    continue;
                }
                if (id.equals(PROPERTY_ISSUE)) {
                    readPropertyFromXml(node);
                } else if (id.equals(DEX_ISSUE)) {
                    readDexPatternsFromXml(node);
                } else if (id.equals(SO_ISSUE)) {
                    readLibPatternsFromXml(node);
                } else if (id.equals(PACKAGECONFIG_ISSUE)) {
                    readPackageConfigFromXml(node);
                } else if (id.equals(SIGN_ISSUE)) {
                    if (mUseSignAPk) {
                        readSignFromXml(node);
                    }
                } else {
                    System.err.println("unknown issue " + id);
                }
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    System.exit(-1);
                }
            }
        }
    }

    private void readPropertyFromXml(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String tagName = check.getTagName();
                    String value = check.getAttribute(ATTR_VALUE);
                    if (value.length() == 0) {
                        throw new IOException(
                            String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
                        );
                    }
                    if (tagName.equals(ATTR_IGNORE_WARNING)) {
                        mIgnoreWarning = value.equals("true");
                    } else if (tagName.equals(ATTR_USE_SIGN)) {
                        mUseSignAPk = value.equals("true");
                    } else if (tagName.equals(ATTR_SEVEN_ZIP_PATH)) {
                        File sevenZipFile = new File(value);
                        if (sevenZipFile.exists()) {
                            mSevenZipPath = value;
                        } else {
                            mSevenZipPath = "7za";
                        }
                    } else {
                        System.err.println("unknown property tag " + tagName);
                    }
                }
            }
        }
    }


    private void readSignFromXml(Node node) throws IOException {
        if (mSignatureFile != null) {
            System.err.println("already set the sign info from command line, ignore this");
            return;
        }
        NodeList childNodes = node.getChildNodes();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String tagName = check.getTagName();
                    String value = check.getAttribute(ATTR_VALUE);
                    if (value.length() == 0) {
                        throw new IOException(
                            String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
                        );
                    }

                    if (tagName.equals(ATTR_SIGNFILE_PATH)) {
                        mSignatureFile = new File(value);
                        if (!mSignatureFile.exists()) {
                            throw new IOException(
                                String.format("the signature file do not exit, raw path= %s\n", mSignatureFile.getAbsolutePath())
                            );
                        }
                    } else if (tagName.equals(ATTR_SIGNFILE_STOREPASS)) {
                        mStorePass = value;
                        mStorePass = mStorePass.trim();
                    } else if (tagName.equals(ATTR_SIGNFILE_KEYPASS)) {
                        mKeyPass = value;
                        mKeyPass = mKeyPass.trim();
                    } else if (tagName.equals(ATTR_SIGNFILE_ALIAS)) {
                        mStoreAlias = value;
                        mStoreAlias = mStoreAlias.trim();
                    } else {
                        System.err.println("unknown sign tag " + tagName);
                    }
                }
            }
        }

    }

    private void readDexPatternsFromXml(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String tagName = check.getTagName();

                    String value = check.getAttribute(ATTR_VALUE);
                    if (tagName.equals(ATTR_DEXMODE)) {
                        if (value.equals("raw")) {
                            mDexRaw = true;
                        }
                    } else if (tagName.equals(ATTR_PATTERN)) {
                        addToPatterns(value, mDexFilePattern);
                    } else if (tagName.equals(ATTR_LOADER)) {
                        mDexLoaderPattern.add(value);
                    } else {
                        System.err.println("unknown dex tag " + tagName);
                    }
                }
            }
        }
    }

    private void readLibPatternsFromXml(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String tagName = check.getTagName();

                    String value = check.getAttribute(ATTR_VALUE);
                    if (tagName.equals(ATTR_PATTERN)) {
                        addToPatterns(value, mSoFilePattern);
                    } else {
                        System.err.println("unknown dex tag " + tagName);
                    }
                }
            }
        }
    }

    private void readPackageConfigFromXml(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String tagName = check.getTagName();

                    String value = check.getAttribute(ATTR_VALUE);
                    String name = check.getAttribute(ATTR_NAME);

                    if (tagName.equals(ATTR_CONFIG_FIELD)) {
                        mPackageFileds.put(name, value);
                    } else {
                        System.err.println("unknown package config tag " + tagName);
                    }
                }
            }
        }
    }

    private void addToPatterns(String value, HashSet<Pattern> patterns) throws IOException {
        if (value.length() == 0) {
            throw new IOException(
                String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
            );
        }
        value = Utils.convetToPatternString(value);
        Pattern pattern = Pattern.compile(value);
        patterns.add(pattern);
    }

}


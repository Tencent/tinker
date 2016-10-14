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

package com.tencent.tinker.patch;


import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.patch.Runner;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by zhangshaowen on 2/27/16.
 * do not use Logger here
 */
public class CliMain extends Runner {
    private static final String ARG_HELP   = "--help";
    private static final String ARG_OUT    = "-out";
    private static final String ARG_CONFIG = "-config";
    private static final String ARG_OLD    = "-old";
    private static final String ARG_NEW    = "-new";


    protected static String mRunningLocation;

    public static void main(String[] args) {
        mBeginTime = System.currentTimeMillis();
        CliMain m = new CliMain();
        setRunningLocation(m);
        m.run(args);
    }

    private static void setRunningLocation(CliMain m) {
        mRunningLocation = m.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            mRunningLocation = URLDecoder.decode(mRunningLocation, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (mRunningLocation.endsWith(".jar")) {
            mRunningLocation = mRunningLocation.substring(0, mRunningLocation.lastIndexOf(File.separator) + 1);
        }
        File f = new File(mRunningLocation);
        mRunningLocation = f.getAbsolutePath();
    }

    private static void printUsage(PrintStream out) {
        // TODO: Look up launcher script name!
        String command = "tinker.jar"; //$NON-NLS-1$
        out.println();
        out.println();
        out.println("Usage: java -jar " + command + " " + ARG_OLD + " old.apk " + ARG_NEW + " new.apk " + ARG_CONFIG + " tinker_config.xml " + ARG_OUT + " output_path");
        out.println("others please contact us");
    }

    private void run(String[] args) {
        if (args.length < 1) {
            goToError();
        }
        try {

            ReadArgs readArgs = new ReadArgs(args).invoke();
            File configFile = readArgs.getConfigFile();
            File outputFile = readArgs.getOutputFile();
            File oldApkFile = readArgs.getOldApkFile();
            File newApkFile = readArgs.getNewApkFile();

            if (oldApkFile == null || newApkFile == null) {
                Logger.e("Missing old apk or new apk file argument");
                goToError();
            } else if (!oldApkFile.exists() || !newApkFile.exists()) {
                Logger.e("Old apk or new apk file does not exist");
                goToError();
            }

            if (outputFile == null) {
                outputFile = new File(mRunningLocation, TypedValue.PATH_DEFAULT_OUTPUT);
            }

            loadConfigFromXml(configFile, outputFile, oldApkFile, newApkFile);
            Logger.initLogger(config);
            tinkerPatch();
        } catch (IOException e) {
            e.printStackTrace();
            goToError();
        } finally {
            Logger.closeLogger();
        }
    }

    private void loadConfigFromXml(File configFile, File outputFile, File oldApkFile, File newApkFile) {
        if (configFile == null) {
            configFile = new File(mRunningLocation + File.separator + TypedValue.FILE_CONFIG);
            if (!configFile.exists()) {
                System.err.printf("the config file %s does not exit\n", configFile.getAbsolutePath());
                printUsage(System.err);
                System.exit(ERRNO_USAGE);
            }
        }
        try {
            config = new Configuration(configFile, outputFile, oldApkFile, newApkFile);

        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            goToError();
        } catch (TinkerPatchException e) {
            e.printStackTrace();
            goToError();
        }
    }

    public void goToError() {
        printUsage(System.err);
        System.exit(ERRNO_USAGE);
    }

    private class ReadArgs {
        private String[] args;
        private File     configFile;
        private File     outputFile;
        private File     oldApkFile;
        private File     newApkFile;

        ReadArgs(String[] args) {
            this.args = args;
        }

        public File getConfigFile() {
            return configFile;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public File getOldApkFile() {
            return oldApkFile;
        }

        public File getNewApkFile() {
            return newApkFile;
        }

        public ReadArgs invoke() {
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (arg.equals(ARG_HELP) || arg.equals("-h")) {
                    goToError();
                } else if (arg.equals(ARG_CONFIG)) {
                    if (index == args.length - 1 || !args[index + 1].endsWith(TypedValue.FILE_XML)) {
                        System.err.println("Missing XML configuration file argument");
                        goToError();
                    }
                    configFile = new File(args[++index]);
                    if (!configFile.exists()) {
                        System.err.println(configFile.getAbsolutePath() + " does not exist");
                        goToError();
                    }

                    System.out.println("special configFile file path:" + configFile.getAbsolutePath());

                } else if (arg.equals(ARG_OUT)) {
                    if (index == args.length - 1) {
                        System.err.println("Missing output file argument");
                        goToError();
                    }
                    outputFile = new File(args[++index]);
                    File parent = outputFile.getParentFile();
                    if (parent != null && (!parent.exists())) {
                        parent.mkdirs();
                    }
                    System.out.printf("special output directory path: %s\n", outputFile.getAbsolutePath());

                } else if (arg.equals(ARG_OLD)) {
                    if (index == args.length - 1) {
                        System.err.println("Missing old apk file argument");
                        goToError();
                    }
                    oldApkFile = new File(args[++index]);
                } else if (arg.equals(ARG_NEW)) {
                    if (index == args.length - 1) {
                        System.err.println("Missing new apk file argument");
                        goToError();
                    }
                    newApkFile = new File(args[++index]);
                }
            }
            return this;
        }
    }


}


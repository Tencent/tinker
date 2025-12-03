package com.tencent.tinker.build.util;

import com.tencent.tinker.bsdiff.BSDiff;
import com.tencent.tinker.build.patch.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class DiffFactory {

    private static boolean diffShellPermission = false;

    public static void diffFile(Configuration config, File oldFile, File newFile, File diffFile) throws IOException {
        Logger.d("path:" + config.mCustomDiffPath + " oldFile:" + oldFile.getPath());
        if (CustomDiff.checkHasCustomDiff(config)) {
            if (!diffShellPermission) {
                diffShellPermission = true;
                makeSurePermission(config.mCustomDiffPath);
            }
            CustomDiff.diffFile(config.mCustomDiffPath, config.mCustomDiffPathArgs, oldFile, newFile, diffFile);
        } else {
            BSDiff.bsdiff(oldFile, newFile, diffFile);
        }
    }

    private static void makeSurePermission(String path) throws IOException {
        try {
            Process process = new ProcessBuilder("chmod", "777", path.split(" ")[0]).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                Logger.d(line);
            }
            int exitCode = process.waitFor();
            Logger.d("run makeSurePermission done, exitCode: " + exitCode);
            process.destroy();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}

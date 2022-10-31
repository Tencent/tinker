package com.tencent.tinker.build.util;

import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomDiff {

    public static boolean checkHasCustomDiff(Configuration config) {
        return config.mCustomDiffPath != null && !config.mCustomDiffPath.trim().isEmpty() && config.mCustomDiffPathArgs != null && !config.mCustomDiffPathArgs.isEmpty();
    }

    public static void diffFile(String mCustomDiffPath,String mCustomDiffPathArgs, File oldFile, File newFile, File diffFile) throws IOException {

        String outPath = diffFile.getAbsolutePath();
        String cmd = mCustomDiffPath;
        List<String> cmds = new ArrayList<>();
        for (String s : cmd.split(" ")) {
            if (!s.isEmpty()) {
                cmds.add(s);
            }
        }
        for (String s : mCustomDiffPathArgs.split(" ")) {
            if (!s.isEmpty()) {
                cmds.add(s);
            }
        }
        cmds.add(oldFile.getAbsolutePath());
        cmds.add(newFile.getAbsolutePath());
        cmds.add(outPath);

        System.out.println(cmd);
        for (String s : cmds) {
            System.out.print(s + " ");
        }
        System.out.println();

        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.redirectErrorStream(true);
        pb.inheritIO();
        Process pro = null;
        LineNumberReader reader = null;
        try {
            boolean isWindows = System.getProperty("os.name")
                    .toLowerCase().startsWith("windows");
            ProcessBuilder builder;
            if (isWindows) {
                cmds.add(0, "cmd.exe");
                builder = new ProcessBuilder(cmds);
            } else {
                builder = new ProcessBuilder(cmds);
            }
            Process process = builder.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                Logger.d(line);
            }
            int exitCode = process.waitFor();
            Logger.d( "run init script done, exitCode: %d", exitCode);
            process.destroy();
        } catch (IOException e) {
            FileOperation.deleteFile(diffFile);
            Logger.e("CustomDecoder error" + e.getMessage());
        } catch (InterruptedException e) {
            Logger.e("CustomDecoder error" + e.getMessage());
        } finally {
            //destroy the stream
            try {
                pro.waitFor();
            } catch (Throwable ignored) {
                // Ignored.
            }
            try {
                pro.destroy();
            } catch (Throwable ignored) {
                // Ignored.
            }
            IOHelper.closeQuietly(reader);
        }
    }

}

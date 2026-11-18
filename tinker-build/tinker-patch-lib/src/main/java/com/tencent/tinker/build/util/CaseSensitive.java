package com.tencent.tinker.build.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CaseSensitive {

  public static boolean caseInsensitiveCompat = false;
  private static final String PREFIX = "CS";
  private static final String UNDERLINE = "_";
  private static long seq = System.currentTimeMillis();

  // <Original Filename, Wrapped Filename>
  private static final Map<String, String> wrappedFileMap = new HashMap<>();

  /**
   * return original file if no conflict files detected, otherwise return a wrapped file with a unique filename.
   *
   * "conflict files" means 2 different filenames point to a same File in case insensitive os (like macOS)
   *
   * eg： File aBc.xml exists, input: abC.xml, return: CS12345678_abC.xml
   *
   * @param target original target file
   */
  public static File wrap(File target) {
    if (!target.exists()) {
      return target;
    }
    File parentFile = target.getParentFile();
    if (parentFile == null || !parentFile.exists()) {
      return target;
    }
    File[] list = parentFile.listFiles();
    if (list == null || list.length == 0) {
      return target;
    }

    String filename = target.getName();
    String oldName = "";
    for (File child : list) {
      if (child.getName().equalsIgnoreCase(filename)) {
        oldName = child.getName();
        break;
      }
    }
    if (oldName.isEmpty() || oldName.equals(filename)) {
      return target;
    }
    if (!caseInsensitiveCompat) {
      Logger.e("find conflict files " + oldName + " and " + filename);
      return target;
    }
    if (wrappedFileMap.containsKey(filename)) {
      return new File(parentFile, wrappedFileMap.get(filename));
    }

    File wrappedFile = new File(parentFile, PREFIX + (seq++) + UNDERLINE + filename);
    wrappedFileMap.put(filename, wrappedFile.getName());
    Logger.d("find conflict file exists:" + oldName + ", wrapped:" + wrappedFile);
    return wrappedFile;
  }

  /**
   * eg:
   * input: CS12345678_abc.xml
   * return: abc.xml
   */
  public static String getOriginalFileName(String name) {
    if (!caseInsensitiveCompat
        || name == null
        || name.isEmpty()
        || !name.startsWith(PREFIX)
        || !wrappedFileMap.containsValue(name)) {
      return name;
    }
    int index = name.indexOf(UNDERLINE);
    if (index < 0) {
      return name;
    }
    try {
      long verifyLong = Long.parseLong(name.substring(2, index));
    } catch (NumberFormatException e) {
      return name;
    }
    return name.substring(index + 1);
  }

  /**
   * eg:
   * input: res/CS12345678_abc.xml
   * return: res/abc.xml
   */
  public static String getOriginalEntryName(String entryName) {
    if (!caseInsensitiveCompat
        || entryName == null
        || entryName.isEmpty()) {
      return entryName;
    }
    int lastSeparatorIndex = entryName.lastIndexOf(File.separator);
    if (lastSeparatorIndex < 0) {
      return getOriginalFileName(entryName);
    }
    String path = entryName.substring(0, lastSeparatorIndex);
    String name = entryName.substring(lastSeparatorIndex + 1);
    name = getOriginalFileName(name);
    return path + File.separator + name;
  }

}

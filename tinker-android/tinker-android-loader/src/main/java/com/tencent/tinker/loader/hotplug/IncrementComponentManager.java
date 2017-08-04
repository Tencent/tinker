package com.tencent.tinker.loader.hotplug;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Build;
import android.util.Xml;

import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tangyinsheng on 2017/8/3.
 */

public final class IncrementComponentManager {
    private static final String TAG = "Tinker.IncrementComponentManager";

    private static volatile boolean sInitialized = false;
    private static final Map<String, ActivityInfo> sClassNameToActivityInfoMap = new HashMap<>();

    public static synchronized void init(Context context, ShareSecurityCheck checker) throws IOException {
        // TODO finish this method.
        final String xmlMeta = checker.getMetaContentMap().get(EnvConsts.INCCOMPONENT_META_FILE);
        StringReader sr = new StringReader(xmlMeta);
        try {
            final XmlPullParser parser = Xml.newPullParser();
            parser.setInput(sr);
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        if ("activity".equalsIgnoreCase(tagName)) {
                            parseActivity(context, parser);
                        }
                        // TODO parser rest type of components.
                        break;
                    default:
                        break;
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        } finally {
            SharePatchFileUtil.closeQuietly(sr);
        }

        sInitialized = true;
    }

    private static synchronized void parseActivity(Context context, XmlPullParser parser) throws XmlPullParserException {
        final Resources resources = context.getResources();
        final ApplicationInfo appInfo = context.getApplicationInfo();
        final ActivityInfo info = new ActivityInfo();
        final int attrCount = parser.getAttributeCount();
        final String defPkgName = context.getPackageName();

        boolean isUIOptionsSet = false;
        for (int attrId = 0; attrId < attrCount; ++attrId) {
            final String attrPrefix = parser.getAttributePrefix(attrId);
            if (!"android".equals(attrPrefix)) {
                continue;
            }
            final String attrName = parser.getAttributeName(attrId);
            final String attrValue = parser.getAttributeValue(attrId);
            if (Build.VERSION.SDK_INT >= 14) {
                if ("uiOptions".equals(attrName)) {
                    info.uiOptions = Integer.decode(attrValue);
                    isUIOptionsSet = true;
                }
            }
            if ("exported".equals(attrName)) {
                info.exported = "true".equalsIgnoreCase(attrValue);
            } else if ("theme".equals(attrName)) {
                info.theme = resources.getIdentifier(attrValue, "resource", defPkgName);
            } else if ("".equals(attrName)) {
                // TODO
            }
        }

        if (info.theme == 0) {
            info.theme = appInfo.theme;
        } else if (Build.VERSION.SDK_INT >= 14 && !isUIOptionsSet) {
            info.uiOptions = appInfo.uiOptions;
        }

        info.applicationInfo = appInfo;
    }

    private static synchronized void ensureInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("Not initialized!!");
        }
    }

    public static boolean isIncrementActivity(String className) {
        ensureInitialized();
        return sClassNameToActivityInfoMap.containsKey(className);
    }

    public static ActivityInfo queryActivityInfo(String className) {
        ensureInitialized();
        return sClassNameToActivityInfoMap.get(className);
    }

    private IncrementComponentManager() {
        throw new UnsupportedOperationException();
    }
}

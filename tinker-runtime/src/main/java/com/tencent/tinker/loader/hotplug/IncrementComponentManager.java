package com.tencent.tinker.loader.hotplug;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tangyinsheng on 2017/8/3.
 */

public final class IncrementComponentManager {
    private static final String TAG = "Tinker.IncrementCompMgr";

    private static final int TAG_ACTIVITY = 0;
    private static final int TAG_SERVICE = 1;
    private static final int TAG_PROVIDER = 2;
    private static final int TAG_RECEIVER = 3;

    private static Context sContext = null;
    private static String sPackageName = null;
    private static volatile boolean sInitialized = false;
    private static final Map<String, ActivityInfo> CLASS_NAME_TO_ACTIVITY_INFO_MAP = new HashMap<>();
    private static final Map<String, IntentFilter> CLASS_NAME_TO_INTENT_FILTER_MAP = new HashMap<>();


    private static abstract class AttrTranslator<T_RESULT> {
        final void translate(Context context, int tagType, XmlPullParser parser, T_RESULT result) {
            onInit(context, tagType, parser);

            final int attrCount = parser.getAttributeCount();
            for (int i = 0; i < attrCount; ++i) {
                final String attrPrefix = parser.getAttributePrefix(i);
                if (!"android".equals(attrPrefix)) {
                    continue;
                }
                final String attrName = parser.getAttributeName(i);
                final String attrValue = parser.getAttributeValue(i);
                onTranslate(context, tagType, attrName, attrValue, result);
            }
        }

        void onInit(Context context, int tagType, XmlPullParser parser) {
            // Do nothing.
        }

        abstract void onTranslate(Context context, int tagType, String attrName, String attrValue, T_RESULT result);
    }

    private static final AttrTranslator<ActivityInfo> ACTIVITY_INFO_ATTR_TRANSLATOR = new AttrTranslator<ActivityInfo>() {

        @Override
        void onInit(Context context, int tagType, XmlPullParser parser) {
            try {
                if (tagType == TAG_ACTIVITY
                        && (parser.getEventType() != XmlPullParser.START_TAG
                        || !"activity".equals(parser.getName()))) {
                    throw new IllegalStateException("unexpected xml parser state when parsing incremental component manifest.");
                }
            } catch (XmlPullParserException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        void onTranslate(Context context, int tagType, String attrName, String attrValue, ActivityInfo result) {
            if ("name".equals(attrName)) {
                if (attrValue.charAt(0) == '.') {
                    result.name = context.getPackageName() + attrValue;
                } else {
                    result.name = attrValue;
                }
            } else if ("parentActivityName".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 16) {
                    if (attrValue.charAt(0) == '.') {
                        result.parentActivityName = context.getPackageName() + attrValue;
                    } else {
                        result.parentActivityName = attrValue;
                    }
                }
            } else if ("exported".equals(attrName)) {
                result.exported = "true".equalsIgnoreCase(attrValue);
            } else if ("launchMode".equals(attrName)) {
                result.launchMode = parseLaunchMode(attrValue);
            } else if ("theme".equals(attrName)) {
                final Resources res = context.getResources();
                final String packageName = context.getPackageName();
                result.theme = res.getIdentifier(attrValue, "style", packageName);
            } else if ("uiOptions".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 14) {
                    result.uiOptions = Integer.decode(attrValue);
                }
            } else if ("permission".equals(attrName)) {
                result.permission = attrValue;
            } else if ("taskAffinity".equals(attrName)) {
                result.taskAffinity = attrValue;
            } else if ("multiprocess".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_MULTIPROCESS;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_MULTIPROCESS;
                }
            } else if ("finishOnTaskLaunch".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH;
                }
            } else if ("clearTaskOnLaunch".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH;
                }
            } else if ("noHistory".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_NO_HISTORY;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_NO_HISTORY;
                }
            } else if ("alwaysRetainTaskState".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE;
                }
            } else if ("stateNotNeeded".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_STATE_NOT_NEEDED;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_STATE_NOT_NEEDED;
                }
            } else if ("excludeFromRecents".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
                }
            } else if ("allowTaskReparenting".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_ALLOW_TASK_REPARENTING;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_ALLOW_TASK_REPARENTING;
                }
            } else if ("finishOnCloseSystemDialogs".equals(attrName)) {
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS;
                } else {
                    result.flags &= ~ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS;
                }
            } else if ("showOnLockScreen".equals(attrName) || "showForAllUsers".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 23) {
                    final int flag = ShareReflectUtil
                            .getValueOfStaticIntField(ActivityInfo.class, "FLAG_SHOW_FOR_ALL_USERS", 0);
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= flag;
                    } else {
                        result.flags &= ~flag;
                    }
                }
            } else if ("immersive".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 18) {
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= ActivityInfo.FLAG_IMMERSIVE;
                    } else {
                        result.flags &= ~ActivityInfo.FLAG_IMMERSIVE;
                    }
                }
            } else if ("hardwareAccelerated".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 11) {
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= ActivityInfo.FLAG_HARDWARE_ACCELERATED;
                    } else {
                        result.flags &= ~ActivityInfo.FLAG_HARDWARE_ACCELERATED;
                    }
                }
            } else if ("documentLaunchMode".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    result.documentLaunchMode = Integer.decode(attrValue);
                }
            } else if ("maxRecents".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    result.maxRecents = Integer.decode(attrValue);
                }
            } else if ("configChanges".equals(attrName)) {
                result.configChanges = Integer.decode(attrValue);
            } else if ("windowSoftInputMode".equals(attrName)) {
                result.softInputMode = Integer.decode(attrValue);
            } else if ("persistableMode".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    result.persistableMode = Integer.decode(attrValue);
                }
            } else if ("allowEmbedded".equals(attrName)) {
                final int flag = ShareReflectUtil
                        .getValueOfStaticIntField(ActivityInfo.class, "FLAG_ALLOW_EMBEDDED", 0);
                if ("true".equalsIgnoreCase(attrValue)) {
                    result.flags |= flag;
                } else {
                    result.flags &= ~flag;
                }
            } else if ("autoRemoveFromRecents".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS;
                    } else {
                        result.flags &= ~ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS;
                    }
                }
            } else if ("relinquishTaskIdentity".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY;
                    } else {
                        result.flags &= ~ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY;
                    }
                }
            } else if ("resumeWhilePausing".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if ("true".equalsIgnoreCase(attrValue)) {
                        result.flags |= ActivityInfo.FLAG_RESUME_WHILE_PAUSING;
                    } else {
                        result.flags &= ~ActivityInfo.FLAG_RESUME_WHILE_PAUSING;
                    }
                }
            } else if ("screenOrientation".equals(attrName)) {
                result.screenOrientation = parseScreenOrientation(attrValue);
            } else if ("label".equals(attrName)) {
                final String strOrResId = attrValue;
                int id = 0;
                try {
                    id = context.getResources().getIdentifier(strOrResId, "string", sPackageName);
                } catch (Throwable ignored) {
                    // Ignored.
                }
                if (id != 0) {
                    result.labelRes = id;
                } else {
                    result.nonLocalizedLabel = strOrResId;
                }
            } else if ("icon".equals(attrName)) {
                try {
                    result.icon = context.getResources().getIdentifier(attrValue, null, sPackageName);
                } catch (Throwable ignored) {
                    // Ignored.
                }
            } else if ("banner".equals(attrName)) {
                if (Build.VERSION.SDK_INT >= 20) {
                    try {
                        result.banner = context.getResources().getIdentifier(attrValue, null, sPackageName);
                    } catch (Throwable ignored) {
                        // Ignored.
                    }
                }
            } else if ("logo".equals(attrName)) {
                try {
                    result.logo = context.getResources().getIdentifier(attrValue, null, sPackageName);
                } catch (Throwable ignored) {
                    // Ignored.
                }
            }
        }

        private int parseLaunchMode(String attrValue) {
            if ("standard".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.LAUNCH_MULTIPLE;
            } else if ("singleTop".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.LAUNCH_SINGLE_TOP;
            } else if ("singleTask".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.LAUNCH_SINGLE_TASK;
            } else if ("singleInstance".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.LAUNCH_SINGLE_INSTANCE;
            } else {
                ShareTinkerLog.w(TAG, "Unknown launchMode: " + attrValue);
                return ActivityInfo.LAUNCH_MULTIPLE;
            }
        }

        private int parseScreenOrientation(String attrValue) {
            if ("unspecified".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            } else if ("behind".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_BEHIND;
            } else if ("landscape".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            } else if ("portrait".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else if ("reverseLandscape".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            } else if ("reversePortrait".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            } else if ("sensorLandscape".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            } else if ("sensorPortrait".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
            } else if ("sensor".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR;
            } else if ("fullSensor".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            } else if ("nosensor".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            } else if ("user".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_USER;
            } else if (Build.VERSION.SDK_INT >= 18 && "fullUser".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
            } else if (Build.VERSION.SDK_INT >= 18 && "locked".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_LOCKED;
            } else if (Build.VERSION.SDK_INT >= 18 && "userLandscape".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
            } else if (Build.VERSION.SDK_INT >= 18 && "userPortrait".equalsIgnoreCase(attrValue)) {
                return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
            } else {
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            }
        }
    };

    public static synchronized boolean init(Context context, ShareSecurityCheck checker) throws IOException {
        if (!checker.getMetaContentMap().containsKey(EnvConsts.INCCOMPONENT_META_FILE)) {
            ShareTinkerLog.i(TAG, "package has no incremental component meta, skip init.");
            return false;
        }
        while (context instanceof ContextWrapper) {
            final Context baseCtx = ((ContextWrapper) context).getBaseContext();
            if (baseCtx == null) {
                break;
            }
            context = baseCtx;
        }
        sContext = context;
        sPackageName = context.getPackageName();
        final String xmlMeta = checker.getMetaContentMap().get(EnvConsts.INCCOMPONENT_META_FILE);
        StringReader sr = new StringReader(xmlMeta);
        XmlPullParser parser = null;
        try {
            parser = Xml.newPullParser();
            parser.setInput(sr);
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        if ("activity".equalsIgnoreCase(tagName)) {
                            final ActivityInfo aInfo = parseActivity(context, parser);
                            CLASS_NAME_TO_ACTIVITY_INFO_MAP.put(aInfo.name, aInfo);
                        } else if ("service".equalsIgnoreCase(tagName)) {
                            // TODO support service component.
                        } else if ("receiver".equalsIgnoreCase(tagName)) {
                            // TODO support receiver component.
                        } else if ("provider".equalsIgnoreCase(tagName)) {
                            // TODO support provider component.
                        }
                        break;
                    default:
                        break;
                }
                event = parser.next();
            }
            sInitialized = true;
            return true;
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        } finally {
            if (parser != null) {
                try {
                    parser.setInput(null);
                } catch (Throwable ignored) {
                    // Ignored.
                }
            }
            SharePatchFileUtil.closeQuietly(sr);
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized ActivityInfo parseActivity(Context context, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        final ActivityInfo aInfo = new ActivityInfo();
        final ApplicationInfo appInfo = context.getApplicationInfo();

        aInfo.applicationInfo = appInfo;
        aInfo.packageName = sPackageName;
        aInfo.processName = appInfo.processName;
        aInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
        aInfo.permission = appInfo.permission;
        aInfo.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        aInfo.taskAffinity = appInfo.taskAffinity;

        if (Build.VERSION.SDK_INT >= 11 && (appInfo.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
            aInfo.flags |= ActivityInfo.FLAG_HARDWARE_ACCELERATED;
        }

        if (Build.VERSION.SDK_INT >= 21) {
            aInfo.documentLaunchMode = ActivityInfo.DOCUMENT_LAUNCH_NONE;
        }
        if (Build.VERSION.SDK_INT >= 14) {
            aInfo.uiOptions = appInfo.uiOptions;
        }

        ACTIVITY_INFO_ATTR_TRANSLATOR.translate(context, TAG_ACTIVITY, parser, aInfo);

        final int outerDepth = parser.getDepth();
        while (true) {
            final int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT
                    || (type == XmlPullParser.END_TAG && parser.getDepth() <= outerDepth)) {
                break;
            } else if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            final String tagName = parser.getName();
            if ("intent-filter".equalsIgnoreCase(tagName)) {
                parseIntentFilter(context, aInfo.name, parser);
            } else if ("meta-data".equalsIgnoreCase(tagName)) {
                parseMetaData(context, aInfo, parser);
            }
        }

        return aInfo;
    }

    private static synchronized void parseIntentFilter(Context context, String componentName, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        final IntentFilter intentFilter = new IntentFilter();

        final String priorityStr = parser.getAttributeValue(null, "priority");
        if (!TextUtils.isEmpty(priorityStr)) {
            intentFilter.setPriority(Integer.decode(priorityStr));
        }

        final String autoVerify = parser.getAttributeValue(null, "autoVerify");
        if (!TextUtils.isEmpty(autoVerify)) {
            try {
                final Method setAutoVerifyMethod
                        = ShareReflectUtil.findMethod(IntentFilter.class, "setAutoVerify", boolean.class);
                setAutoVerifyMethod.invoke(intentFilter, "true".equalsIgnoreCase(autoVerify));
            } catch (Throwable ignored) {
                // Ignored.
            }
        }

        final int outerDepth = parser.getDepth();
        while (true) {
            final int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT
                    || (type == XmlPullParser.END_TAG && parser.getDepth() <= outerDepth)) {
                break;
            } else if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            final String tagName = parser.getName();
            if ("action".equals(tagName)) {
                final String name = parser.getAttributeValue(null, "name");
                if (name != null) {
                    intentFilter.addAction(name);
                }
            } else if ("category".equals(tagName)) {
                final String name = parser.getAttributeValue(null, "name");
                if (name != null) {
                    intentFilter.addCategory(name);
                }
            } else if ("data".equals(tagName)) {
                final String mimeType = parser.getAttributeValue(null, "mimeType");
                if (mimeType != null) {
                    try {
                        intentFilter.addDataType(mimeType);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        throw new XmlPullParserException("bad mimeType", parser, e);
                    }
                }
                final String scheme = parser.getAttributeValue(null, "scheme");
                if (scheme != null) {
                    intentFilter.addDataScheme(scheme);
                }
                if (Build.VERSION.SDK_INT >= 19) {
                    final String ssp = parser.getAttributeValue(null, "ssp");
                    if (ssp != null) {
                        intentFilter.addDataSchemeSpecificPart(ssp, PatternMatcher.PATTERN_LITERAL);
                    }
                    final String sspPrefix = parser.getAttributeValue(null, "sspPrefix");
                    if (sspPrefix != null) {
                        intentFilter.addDataSchemeSpecificPart(sspPrefix, PatternMatcher.PATTERN_PREFIX);
                    }
                    final String sspPattern = parser.getAttributeValue(null, "sspPattern");
                    if (sspPattern != null) {
                        intentFilter.addDataSchemeSpecificPart(sspPattern, PatternMatcher.PATTERN_SIMPLE_GLOB);
                    }
                }
                final String host = parser.getAttributeValue(null, "host");
                final String port = parser.getAttributeValue(null, "port");
                if (host != null) {
                    intentFilter.addDataAuthority(host, port);
                }
                final String path = parser.getAttributeValue(null, "path");
                if (path != null) {
                    intentFilter.addDataPath(path, PatternMatcher.PATTERN_LITERAL);
                }
                final String pathPrefix = parser.getAttributeValue(null, "pathPrefix");
                if (pathPrefix != null) {
                    intentFilter.addDataPath(pathPrefix, PatternMatcher.PATTERN_PREFIX);
                }
                final String pathPattern = parser.getAttributeValue(null, "pathPattern");
                if (pathPattern != null) {
                    intentFilter.addDataPath(pathPattern, PatternMatcher.PATTERN_SIMPLE_GLOB);
                }
            }
            skipCurrentTag(parser);
        }

        CLASS_NAME_TO_INTENT_FILTER_MAP.put(componentName, intentFilter);
    }

    private static synchronized void parseMetaData(Context context, ActivityInfo aInfo, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        final ClassLoader myCl = IncrementComponentManager.class.getClassLoader();
        final String name = parser.getAttributeValue(null, "name");
        final String value = parser.getAttributeValue(null, "value");
        if (!TextUtils.isEmpty(name)) {
            if (aInfo.metaData == null) {
                aInfo.metaData = new Bundle(myCl);
            }
            aInfo.metaData.putString(name, value);
        }
    }

    private static void skipCurrentTag(XmlPullParser parser) throws IOException, XmlPullParserException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
        }
    }

    private static synchronized void ensureInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("Not initialized!!");
        }
    }

    public static boolean isIncrementActivity(String className) {
        ensureInitialized();
        return className != null && CLASS_NAME_TO_ACTIVITY_INFO_MAP.containsKey(className);
    }

    public static ActivityInfo queryActivityInfo(String className) {
        ensureInitialized();
        return (className != null ? CLASS_NAME_TO_ACTIVITY_INFO_MAP.get(className) : null);
    }

    // TODO needs to support rest type of components.
    public static ResolveInfo resolveIntent(Intent intent) {
        ensureInitialized();

        int maxPriority = -1;
        String bestComponentName = null;
        IntentFilter respFilter = null;
        int bestMatchRes = 0;

        final ComponentName component = intent.getComponent();
        if (component != null) {
            final String compName = component.getClassName();
            if (CLASS_NAME_TO_ACTIVITY_INFO_MAP.containsKey(compName)) {
                bestComponentName = compName;
                maxPriority = 0;
            }
        } else {
            for (Map.Entry<String, IntentFilter> item : CLASS_NAME_TO_INTENT_FILTER_MAP.entrySet()) {
                final String componentName = item.getKey();
                final IntentFilter intentFilter = item.getValue();
                final int matchRes = intentFilter.match(intent.getAction(), intent.getType(),
                        intent.getScheme(), intent.getData(), intent.getCategories(), TAG);
                final boolean matches = (matchRes != IntentFilter.NO_MATCH_ACTION)
                        && (matchRes != IntentFilter.NO_MATCH_CATEGORY)
                        && (matchRes != IntentFilter.NO_MATCH_DATA)
                        && (matchRes != IntentFilter.NO_MATCH_TYPE);
                final int priority = intentFilter.getPriority();
                if (matches && priority > maxPriority) {
                    maxPriority = priority;
                    bestComponentName = componentName;
                    respFilter = intentFilter;
                    bestMatchRes = matchRes;
                }
            }
        }
        if (bestComponentName != null) {
            final ResolveInfo result = new ResolveInfo();
            result.activityInfo = CLASS_NAME_TO_ACTIVITY_INFO_MAP.get(bestComponentName);
            result.filter = respFilter;
            result.match = bestMatchRes;
            result.priority = maxPriority;
            result.resolvePackageName = sPackageName;
            result.icon = result.activityInfo.icon;
            result.labelRes = result.activityInfo.labelRes;
            return result;
        } else {
            return null;
        }
    }

    private IncrementComponentManager() {
        throw new UnsupportedOperationException();
    }
}

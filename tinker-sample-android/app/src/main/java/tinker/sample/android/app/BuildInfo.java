package tinker.sample.android.app;

import tinker.sample.android.BuildConfig;

/**
 * Created by zhangshaowen on 16/6/30.
 * we use BuildInfo instead of {@link BuildInfo} to make less change
 */
public class BuildInfo {
    /**
     * they are not final, so they won't change with the BuildConfig values!
     */
    public static boolean DEBUG        = BuildConfig.DEBUG;
    public static String  VERSION_NAME = BuildConfig.VERSION_NAME;
    public static int     VERSION_CODE = BuildConfig.VERSION_CODE;

    public static String MESSAGE       = BuildConfig.MESSAGE;
    public static String CLIENTVERSION = BuildConfig.CLIENTVERSION;

}

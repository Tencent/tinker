package com.tencent.tinker.loader.hotplug;

import android.content.Context;

/**
 * Created by tangyinsheng on 2017/8/3.
 */

public final class EnvConsts {
    public static final String ACTIVITY_MANAGER_SRVNAME = Context.ACTIVITY_SERVICE;
    public static final String PACKAGE_MANAGER_SRVNAME = "package";

    public static final String INTENT_EXTRA_OLD_COMPONENT = "tinker_iek_old_component";

    // Please keep it synchronized with the other one defined in 'TypedValue' class
    public static final String INCCOMPONENT_META_FILE = "assets/inc_component_meta.txt";

    private EnvConsts() {
        throw new UnsupportedOperationException();
    }
}

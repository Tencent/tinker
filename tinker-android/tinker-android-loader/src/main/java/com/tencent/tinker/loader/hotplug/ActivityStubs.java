package com.tencent.tinker.loader.hotplug;

import android.app.Activity;

/**
 * Created by tangyinsheng on 2017/8/3.
 */

final class ActivityStubs {
    static final String STUB_PACKAGE_NAME = ActivityStubs.class.getPackage().getName();

    static final String STARDARD_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$StandardActivityStub_%02X";
    static final String SINGLETOP_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SingleTopActivityStub_%02X";
    static final String SINGLETASK_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SingleTaskActivityStub_%02X";
    static final String SINGLEINSTANCE_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SingleInstanceActivityStub_%02X";

    static final int STANDARD_STUB_COUNT = 10;
    static final int SINGLETOP_STUB_COUNT = 10;
    static final int SINGLETASK_STUB_COUNT = 10;
    static final int SINGLEINSTANCE_STUB_COUNT = 10;

    public static final class StandardActivityStub_00 extends Activity { }
    public static final class StandardActivityStub_01 extends Activity { }
    public static final class StandardActivityStub_02 extends Activity { }
    public static final class StandardActivityStub_03 extends Activity { }
    public static final class StandardActivityStub_04 extends Activity { }
    public static final class StandardActivityStub_05 extends Activity { }
    public static final class StandardActivityStub_06 extends Activity { }
    public static final class StandardActivityStub_07 extends Activity { }
    public static final class StandardActivityStub_08 extends Activity { }
    public static final class StandardActivityStub_09 extends Activity { }

    public static final class SingleTopActivityStub_00 extends Activity { }
    public static final class SingleTopActivityStub_01 extends Activity { }
    public static final class SingleTopActivityStub_02 extends Activity { }
    public static final class SingleTopActivityStub_03 extends Activity { }
    public static final class SingleTopActivityStub_04 extends Activity { }
    public static final class SingleTopActivityStub_05 extends Activity { }
    public static final class SingleTopActivityStub_06 extends Activity { }
    public static final class SingleTopActivityStub_07 extends Activity { }
    public static final class SingleTopActivityStub_08 extends Activity { }
    public static final class SingleTopActivityStub_09 extends Activity { }

    public static final class SingleTaskActivityStub_00 extends Activity { }
    public static final class SingleTaskActivityStub_01 extends Activity { }
    public static final class SingleTaskActivityStub_02 extends Activity { }
    public static final class SingleTaskActivityStub_03 extends Activity { }
    public static final class SingleTaskActivityStub_04 extends Activity { }
    public static final class SingleTaskActivityStub_05 extends Activity { }
    public static final class SingleTaskActivityStub_06 extends Activity { }
    public static final class SingleTaskActivityStub_07 extends Activity { }
    public static final class SingleTaskActivityStub_08 extends Activity { }
    public static final class SingleTaskActivityStub_09 extends Activity { }

    public static final class SingleInstanceActivityStub_00 extends Activity { }
    public static final class SingleInstanceActivityStub_01 extends Activity { }
    public static final class SingleInstanceActivityStub_02 extends Activity { }
    public static final class SingleInstanceActivityStub_03 extends Activity { }
    public static final class SingleInstanceActivityStub_04 extends Activity { }
    public static final class SingleInstanceActivityStub_05 extends Activity { }
    public static final class SingleInstanceActivityStub_06 extends Activity { }
    public static final class SingleInstanceActivityStub_07 extends Activity { }
    public static final class SingleInstanceActivityStub_08 extends Activity { }
    public static final class SingleInstanceActivityStub_09 extends Activity { }

    private ActivityStubs() {
        throw new UnsupportedOperationException();
    }
}

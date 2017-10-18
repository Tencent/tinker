package com.tencent.tinker.loader.hotplug;

import android.app.Activity;

/**
 * Created by tangyinsheng on 2017/8/3.
 */

final class ActivityStubs {
    static final String STUB_PACKAGE_NAME = ActivityStubs.class.getPackage().getName();

    static final String STARDARD_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$STDStub_%02X";
    static final String SINGLETOP_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SGTStub_%02X";
    static final String SINGLETASK_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SGTKStub_%02X";
    static final String SINGLEINSTANCE_STUB_CLASSNAME_FORMAT
            = STUB_PACKAGE_NAME + "." + ActivityStubs.class.getSimpleName() + "$SIStub_%02X";

    static final String TRANSPARENT_STUB_FORMAT_SUFFIX = "_T";

    static final int STANDARD_STUB_COUNT = 10;
    static final int STANDARD_TRSNAPARENT_STUB_COUNT = 3;
    static final int SINGLETOP_STUB_COUNT = 10;
    static final int SINGLETOP_TRSNAPARENT_STUB_COUNT = 3;
    static final int SINGLETASK_STUB_COUNT = 10;
    static final int SINGLETASK_TRSNAPARENT_STUB_COUNT = 3;
    static final int SINGLEINSTANCE_STUB_COUNT = 10;
    static final int SINGLEINSTANCE_TRSNAPARENT_STUB_COUNT = 3;

    public static final class STDStub_00 extends Activity { }
    public static final class STDStub_01 extends Activity { }
    public static final class STDStub_02 extends Activity { }
    public static final class STDStub_03 extends Activity { }
    public static final class STDStub_04 extends Activity { }
    public static final class STDStub_05 extends Activity { }
    public static final class STDStub_06 extends Activity { }
    public static final class STDStub_07 extends Activity { }
    public static final class STDStub_08 extends Activity { }
    public static final class STDStub_09 extends Activity { }

    public static final class STDStub_00_T extends Activity { }
    public static final class STDStub_01_T extends Activity { }
    public static final class STDStub_02_T extends Activity { }

    public static final class SGTStub_00 extends Activity { }
    public static final class SGTStub_01 extends Activity { }
    public static final class SGTStub_02 extends Activity { }
    public static final class SGTStub_03 extends Activity { }
    public static final class SGTStub_04 extends Activity { }
    public static final class SGTStub_05 extends Activity { }
    public static final class SGTStub_06 extends Activity { }
    public static final class SGTStub_07 extends Activity { }
    public static final class SGTStub_08 extends Activity { }
    public static final class SGTStub_09 extends Activity { }

    public static final class SGTStub_00_T extends Activity { }
    public static final class SGTStub_01_T extends Activity { }
    public static final class SGTStub_02_T extends Activity { }

    public static final class SGTKStub_00 extends Activity { }
    public static final class SGTKStub_01 extends Activity { }
    public static final class SGTKStub_02 extends Activity { }
    public static final class SGTKStub_03 extends Activity { }
    public static final class SGTKStub_04 extends Activity { }
    public static final class SGTKStub_05 extends Activity { }
    public static final class SGTKStub_06 extends Activity { }
    public static final class SGTKStub_07 extends Activity { }
    public static final class SGTKStub_08 extends Activity { }
    public static final class SGTKStub_09 extends Activity { }

    public static final class SGTKStub_00_T extends Activity { }
    public static final class SGTKStub_01_T extends Activity { }
    public static final class SGTKStub_02_T extends Activity { }

    public static final class SIStub_00 extends Activity { }
    public static final class SIStub_01 extends Activity { }
    public static final class SIStub_02 extends Activity { }
    public static final class SIStub_03 extends Activity { }
    public static final class SIStub_04 extends Activity { }
    public static final class SIStub_05 extends Activity { }
    public static final class SIStub_06 extends Activity { }
    public static final class SIStub_07 extends Activity { }
    public static final class SIStub_08 extends Activity { }
    public static final class SIStub_09 extends Activity { }

    public static final class SIStub_00_T extends Activity { }
    public static final class SIStub_01_T extends Activity { }
    public static final class SIStub_02_T extends Activity { }

    private ActivityStubs() {
        throw new UnsupportedOperationException();
    }
}

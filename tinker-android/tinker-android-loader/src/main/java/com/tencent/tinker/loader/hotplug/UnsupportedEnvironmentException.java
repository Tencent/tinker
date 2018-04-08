package com.tencent.tinker.loader.hotplug;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class UnsupportedEnvironmentException extends UnsupportedOperationException {

    public UnsupportedEnvironmentException(String msg) {
        super(msg);
    }

    public UnsupportedEnvironmentException(Throwable thr) {
        super(thr);
    }
}

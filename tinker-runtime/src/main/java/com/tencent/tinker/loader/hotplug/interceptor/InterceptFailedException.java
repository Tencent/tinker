package com.tencent.tinker.loader.hotplug.interceptor;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class InterceptFailedException extends Exception {

    public InterceptFailedException(Throwable thr) {
        super(thr);
    }
}

package com.tencent.tinker.loader.hotplug.interceptor;

import android.os.Handler;
import android.os.Message;

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.reflect.Field;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class HandlerMessageInterceptor extends Interceptor<Handler.Callback> {
    private final Handler mTarget;
    private final MessageHandler mMessageHandler;

    private static Field sMCallbackField = null;

    static {
        synchronized (HandlerMessageInterceptor.class) {
            if (sMCallbackField == null) {
                try {
                    sMCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
                } catch (Throwable ignored) {
                    // ignored.
                }
            }
        }
    }

    public HandlerMessageInterceptor(Handler target, MessageHandler messageHandler) {
        mTarget = target;
        mMessageHandler = messageHandler;
    }

    @Override
    protected Handler.Callback fetchTarget() throws Throwable {
        return (Handler.Callback) sMCallbackField.get(mTarget);
    }

    @Override
    protected Handler.Callback decorate(final Handler.Callback callback) throws Throwable {
        return new CallbackWrapper(mMessageHandler, callback);
    }

    @Override
    protected void inject(Handler.Callback decorated) throws Throwable {
        sMCallbackField.set(mTarget, decorated);
    }

    public interface MessageHandler {
        boolean handleMessage(Message msg);
    }

    private static class CallbackWrapper implements Handler.Callback {
        private final MessageHandler   mMessageHandler;
        private final Handler.Callback mOrigCallback;

        CallbackWrapper(MessageHandler messageHandler, Handler.Callback origCallback) {
            mMessageHandler = messageHandler;
            mOrigCallback = origCallback;
        }

        @Override
        public boolean handleMessage(Message message) {
            if (mMessageHandler.handleMessage(message)) {
                return true;
            } else if (mOrigCallback != null) {
                return mOrigCallback.handleMessage(message);
            } else {
                return false;
            }
        }
    }
}

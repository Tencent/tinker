package com.tencent.tinker.loader.shareutil;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.tencent.tinker.anno.Keep;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.tencent.tinker.loader.shareutil.ShareTinkerLog.FN_LOG_PRINT_PENDING_LOGS;
import static com.tencent.tinker.loader.shareutil.ShareTinkerLog.FN_LOG_PRINT_STACKTRACE;
import static com.tencent.tinker.loader.shareutil.ShareTinkerLog.getDefaultImpl;
import static com.tencent.tinker.loader.shareutil.ShareTinkerLog.getImpl;

/**
 * Created by tangyinsheng on 2020/6/4.
 */
@Keep
final class TinkerLogInlineFence extends Handler {
    private static final String TAG = "Tinker.TinkerLogInlineFence";

    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static final List<Object[]> pendingLogs = new ArrayList<>();

    @Override
    public void handleMessage(Message msg) {
        handleMessage_$noinline$(msg);
    }

    private void handleMessage_$noinline$(Message msg) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            handleMessageImpl(msg);
        }
    }

    private void handleMessageImpl(Message msg) {
        final ShareTinkerLog.TinkerLogImp defaultLogImp = getDefaultImpl();
        final ShareTinkerLog.TinkerLogImp logImp = getImpl();
        switch (msg.what) {
            case Log.VERBOSE: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.v((String) args[2], (String) args[3], (Object[]) args[4]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case Log.DEBUG: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.d((String) args[2], (String) args[3], (Object[]) args[4]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case Log.INFO: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.i((String) args[2], (String) args[3], (Object[]) args[4]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case Log.WARN: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.w((String) args[2], (String) args[3], (Object[]) args[4]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case Log.ERROR: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.e((String) args[2], (String) args[3], (Object[]) args[4]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case FN_LOG_PRINT_STACKTRACE: {
                final Object[] args = (Object[]) msg.obj;
                if (logImp != null) {
                    logImp.printErrStackTrace((String) args[2], (Throwable) args[3], (String) args[4], (Object[]) args[5]);
                }
                if (logImp == null || logImp == defaultLogImp) {
                    synchronized (pendingLogs) {
                        pendingLogs.add(args);
                    }
                }
                break;
            }
            case FN_LOG_PRINT_PENDING_LOGS: {
                printPendingLogs(logImp);
                break;
            }
            default: {
                logImp.e(TAG, "[-] Bad msg id: " + msg.what);
                break;
            }
        }
    }

    private static void printPendingLogs(final ShareTinkerLog.TinkerLogImp logImp) {
        synchronized (pendingLogs) {
            if (logImp == null || pendingLogs.isEmpty()) {
                return;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final SimpleDateFormat timestampFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
                synchronized (pendingLogs) {
                    for (Object[] args : pendingLogs) {
                        final Object[] argsRef = args;
                        mainThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                final String timestamp = timestampFmt.format(new Date((long) argsRef[1]));
                                final String prefix = "[PendingLog @ " + timestamp + "] ";
                                switch ((int) argsRef[0]) {
                                    case Log.VERBOSE: {
                                        logImp.v((String) argsRef[2], prefix + (String) argsRef[3], (Object[]) argsRef[4]);
                                        break;
                                    }
                                    case Log.DEBUG: {
                                        logImp.d((String) argsRef[2], prefix + (String) argsRef[3], (Object[]) argsRef[4]);
                                        break;
                                    }
                                    case Log.INFO: {
                                        logImp.i((String) argsRef[2], prefix + (String) argsRef[3], (Object[]) argsRef[4]);
                                        break;
                                    }
                                    case Log.WARN: {
                                        logImp.w((String) argsRef[2], prefix + (String) argsRef[3], (Object[]) argsRef[4]);
                                        break;
                                    }
                                    case Log.ERROR: {
                                        logImp.e((String) argsRef[2], prefix + (String) argsRef[3], (Object[]) argsRef[4]);
                                        break;
                                    }
                                    case ShareTinkerLog.FN_LOG_PRINT_STACKTRACE: {
                                        logImp.printErrStackTrace((String) argsRef[2], (Throwable) argsRef[3], prefix + (String) argsRef[4], (Object[]) argsRef[5]);
                                        break;
                                    }
                                    default: {
                                        // Ignored.
                                        break;
                                    }
                                }
                            }
                        });
                    }
                    pendingLogs.clear();
                }
            }
        }, "tinker_log_printer").start();
    }

    private static void dummyThrowExceptionMethod() {
        if (TinkerLogInlineFence.class.isPrimitive()) {
            throw new RuntimeException();
        }
    }
}

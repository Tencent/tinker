package com.tencent.tinker.commons.dexpatcher;

/**
 * Created by tangyinsheng on 2016/9/18.
 */

public final class DexPatcherLogger {
    private IDexPatcherLogger loggerImpl = null;

    public void setLoggerImpl(IDexPatcherLogger dexPatcherLogger) {
        this.loggerImpl = dexPatcherLogger;
    }

    public void v(String tag, String fmt, Object... vals) {
        if (this.loggerImpl != null) {
            fmt = "[V][" + tag + "] " + fmt;
            this.loggerImpl.v((vals == null || vals.length == 0) ? fmt : String.format(fmt, vals));
        }
    }

    public void d(String tag, String fmt, Object... vals) {
        if (this.loggerImpl != null) {
            fmt = "[D][" + tag + "] " + fmt;
            this.loggerImpl.d((vals == null || vals.length == 0) ? fmt : String.format(fmt, vals));
        }
    }

    public void i(String tag, String fmt, Object... vals) {
        if (this.loggerImpl != null) {
            fmt = "[I][" + tag + "] " + fmt;
            this.loggerImpl.i((vals == null || vals.length == 0) ? fmt : String.format(fmt, vals));
        }
    }

    public void w(String tag, String fmt, Object... vals) {
        if (this.loggerImpl != null) {
            fmt = "[W][" + tag + "] " + fmt;
            this.loggerImpl.w((vals == null || vals.length == 0) ? fmt : String.format(fmt, vals));
        }
    }

    public void e(String tag, String fmt, Object... vals) {
        if (this.loggerImpl != null) {
            fmt = "[E][" + tag + "] " + fmt;
            this.loggerImpl.e((vals == null || vals.length == 0) ? fmt : String.format(fmt, vals));
        }
    }


    public interface IDexPatcherLogger {
        void v(String msg);

        void d(String msg);

        void i(String msg);

        void w(String msg);

        void e(String msg);
    }
}

package tinker.sample.android.app;

import com.tencent.tinker.loader.app.TinkerApplication;

/**
 * Created by ZhuPeipei on 2020/12/26 19:33.
 */
public class SampleApplication extends TinkerApplication {

    public SampleApplication() {
        super(15, "tinker.sample.android.app.SampleApplicationLike", "com.tencent.tinker.loader.TinkerLoader", false, false);
    }

}

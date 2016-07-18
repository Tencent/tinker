package tinker.sample.android.app;

/**
 * Created by shwenzhang on 16/6/30.
 * we add BaseBuildInfo to loader pattern, so it won't change with patch!
 */
public class BaseBuildInfo {
    public static String TEST_MESSAGE = "I won't change with tinker patch!";
}

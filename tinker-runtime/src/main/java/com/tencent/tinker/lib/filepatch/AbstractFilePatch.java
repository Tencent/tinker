package com.tencent.tinker.lib.filepatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractFilePatch {

    public abstract int patchFast(InputStream oldInputStream, InputStream diffInputStream, File newFile) throws IOException;

}

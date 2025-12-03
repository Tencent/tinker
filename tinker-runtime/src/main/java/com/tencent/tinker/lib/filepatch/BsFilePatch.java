package com.tencent.tinker.lib.filepatch;

import com.tencent.tinker.bsdiff.BSPatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class BsFilePatch extends AbstractFilePatch{

    @Override
    public int patchFast(InputStream oldInputStream, InputStream diffInputStream, File newFile) throws IOException {
        return BSPatch.patchFast(oldInputStream, diffInputStream, newFile);
    }
}

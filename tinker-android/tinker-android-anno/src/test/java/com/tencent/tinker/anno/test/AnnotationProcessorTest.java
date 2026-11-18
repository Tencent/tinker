package com.tencent.tinker.anno.test;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnnotationProcessorTest {

    private static final String TEMPLATE_RESOURCE = "TinkerAnnoApplication.tmpl";

    private static final String[] HOT_PATH_METHODS = {
            "getResources",
            "getAssets",
            "getClassLoader"
    };

    private String readTemplate() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE);
        if (is == null) {
            is = AnnotationProcessorTest.class.getResourceAsStream("/" + TEMPLATE_RESOURCE);
        }
        assertNotNull("Template " + TEMPLATE_RESOURCE + " must be on the test classpath", is);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            is.close();
        }
    }

    private String extractMethodBody(String src, String methodName) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher m = p.matcher(src);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    @Test
    public void hotPathMethods_useDirectDelegation_notReflection() throws IOException {
        String template = readTemplate();
        for (String method : HOT_PATH_METHODS) {
            String body = extractMethodBody(template, method);
            assertNotNull("Template should override " + method + "()", body);
            assertFalse(
                    method + "() must call ApplicationLike directly, not via Method.invoke",
                    body.contains(".invoke(")
            );
            assertTrue(
                    method + "() should delegate to the ApplicationLike instance",
                    body.contains(method + "(")
            );
        }
    }

    @Test
    public void template_doesNotRetainReflectionCachesForHotPath() throws IOException {
        String template = readTemplate();
        for (String method : HOT_PATH_METHODS) {
            String cacheField = "mCurrentMethod_" + method;
            assertFalse(
                    "Reflection Method cache field for hot path method " + method
                            + " should be removed from the template",
                    template.contains(cacheField)
            );
        }
    }
}

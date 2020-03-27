/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.anno;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;


/**
 * Tinker Annotations Processor
 *
 * Created by zhaoyuan on 16/3/31.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class AnnotationProcessor extends AbstractProcessor {

    private static final String SUFFIX                    = "$$DefaultLifeCycle";
    private static final String APPLICATION_TEMPLATE_PATH = "/TinkerAnnoApplication.tmpl";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final Set<String> supportedAnnotationTypes = new LinkedHashSet<>();

        supportedAnnotationTypes.add(DefaultLifeCycle.class.getName());

        return supportedAnnotationTypes;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processDefaultLifeCycle(roundEnv.getElementsAnnotatedWith(DefaultLifeCycle.class));
        return true;
    }


    private void processDefaultLifeCycle(Set<? extends Element> elements) {
        // DefaultLifeCycle
        for (Element e : elements) {
            DefaultLifeCycle ca = e.getAnnotation(DefaultLifeCycle.class);

            String lifeCycleClassName = ((TypeElement) e).getQualifiedName().toString();
            String lifeCyclePackageName = lifeCycleClassName.substring(0, lifeCycleClassName.lastIndexOf('.'));
            lifeCycleClassName = lifeCycleClassName.substring(lifeCycleClassName.lastIndexOf('.') + 1);

            String applicationClassName = ca.application();
            if (applicationClassName.startsWith(".")) {
                applicationClassName = lifeCyclePackageName + applicationClassName;
            }
            String applicationPackageName = applicationClassName.substring(0, applicationClassName.lastIndexOf('.'));
            applicationClassName = applicationClassName.substring(applicationClassName.lastIndexOf('.') + 1);

            String loaderClassName = ca.loaderClass();
            if (loaderClassName.startsWith(".")) {
                loaderClassName = lifeCyclePackageName + loaderClassName;
            }

            final InputStream is = AnnotationProcessor.class.getResourceAsStream(APPLICATION_TEMPLATE_PATH);
            final Scanner scanner = new Scanner(is);
            final String template = scanner.useDelimiter("\\A").next();
            final String fileContent = template
                .replaceAll("%PACKAGE%", applicationPackageName)
                .replaceAll("%APPLICATION%", applicationClassName)
                .replaceAll("%APPLICATION_LIFE_CYCLE%", lifeCyclePackageName + "." + lifeCycleClassName)
                .replaceAll("%TINKER_FLAGS%", "" + ca.flags())
                .replaceAll("%TINKER_LOADER_CLASS%", "" + loaderClassName)
                .replaceAll("%TINKER_LOAD_VERIFY_FLAG%", "" + ca.loadVerifyFlag());

            try {
                JavaFileObject fileObject = processingEnv.getFiler().createSourceFile(applicationPackageName + "." + applicationClassName);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Creating " + fileObject.toUri());
                Writer writer = fileObject.openWriter();
                try {
                    PrintWriter pw = new PrintWriter(writer);
                    pw.print(fileContent);
                    pw.flush();

                } finally {
                    writer.close();
                }
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
            }
        }
    }
}

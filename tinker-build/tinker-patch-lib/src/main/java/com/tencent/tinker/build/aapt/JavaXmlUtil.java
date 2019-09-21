/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.tencent.tinker.build.aapt;

import com.tencent.tinker.commons.util.IOHelper;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public final class JavaXmlUtil {

    /**
     * get document builder
     *
     * @return DocumentBuilder
     */
    private static DocumentBuilder getDocumentBuilder() {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            // Block any external content resolving actions since we don't need them and a report
            // says these actions may cause security problems.
            documentBuilder.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                    return new InputSource();
                }
            });
        } catch (Exception e) {
            throw new JavaXmlUtilException(e);
        }
        return documentBuilder;
    }

    public static Document getEmptyDocument() {
        Document document = null;
        try {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            document = documentBuilder.newDocument();
            document.normalize();
        } catch (Exception e) {
            throw new JavaXmlUtilException(e);
        }
        return document;
    }

    /**
     * parse
     *
     * @param filename
     * @return Document
     */
    public static Document parse(final String filename) {
        Document document = null;
        try {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            document = documentBuilder.parse(new File(filename));
            document.normalize();
        } catch (Exception e) {
            throw new JavaXmlUtilException(e);
        }
        return document;
    }

    /**
     * parse
     *
     * @param inputStream
     * @return Document
     */
    public static Document parse(final InputStream inputStream) {
        Document document = null;
        try {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            document = documentBuilder.parse(inputStream);
            document.normalize();
        } catch (Exception e) {
            throw new JavaXmlUtilException(e);
        }
        return document;
    }

    /**
     * save document
     *
     * @param document
     * @param outputFullFilename
     */
    public static void saveDocument(final Document document, final String outputFullFilename) {
        OutputStream outputStream = null;
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(document);
            transformer.setOutputProperty(OutputKeys.ENCODING, Constant.Encoding.UTF8);
            outputStream = new FileOutputStream(outputFullFilename);
            StreamResult result = new StreamResult(outputStream);
            transformer.transform(domSource, result);
        } catch (Exception e) {
            throw new JavaXmlUtilException(e);
        } finally {
            IOHelper.closeQuietly(outputStream);
        }
    }

    public static class JavaXmlUtilException extends RuntimeException {
        private static final long serialVersionUID = 4669527982017700891L;

        public JavaXmlUtilException(Throwable cause) {
            super(cause);
        }
    }
}

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

public interface Constant {

    interface Base {
        String EXCEPTION = "exception";
    }

    interface Symbol {
        /**
         * dot "."
         */
        String DOT                  = ".";
        char   DOT_CHAR             = '.';
        /**
         * comma ","
         */
        String COMMA                = ",";
        /**
         * colon ":"
         */
        String COLON                = ":";
        /**
         * semicolon ";"
         */
        String SEMICOLON            = ";";
        /**
         * equal "="
         */
        String EQUAL                = "=";
        /**
         * and "&"
         */
        String AND                  = "&";
        /**
         * question mark "?"
         */
        String QUESTION_MARK        = "?";
        /**
         * wildcard "*"
         */
        String WILDCARD             = "*";
        /**
         * underline "_"
         */
        String UNDERLINE            = "_";
        /**
         * at "@"
         */
        String AT                   = "@";
        /**
         * minus "-"
         */
        String MINUS                = "-";
        /**
         * logic and "&&"
         */
        String LOGIC_AND            = "&&";
        /**
         * logic or "||"
         */
        String LOGIC_OR             = "||";
        /**
         * brackets begin "("
         */
        String BRACKET_LEFT         = "(";
        /**
         * brackets end ")"
         */
        String BRACKET_RIGHT        = ")";
        /**
         * middle bracket left "["
         */
        String MIDDLE_BRACKET_LEFT  = "[";
        /**
         * middle bracket right "]"
         */
        String MIDDLE_BRACKET_RIGHT = "]";
        /**
         * big bracket "{"
         */
        String BIG_BRACKET_LEFT     = "{";
        /**
         * big bracket "}"
         */
        String BIG_BRACKET_RIGHT    = "}";
        /**
         * slash "/"
         */
        String SLASH_LEFT           = "/";
        /**
         * slash "\"
         */
        String SLASH_RIGHT          = "\\";
        /**
         * xor or regex begin "^"
         */
        String XOR                  = "^";
        /**
         * dollar or regex end "$"
         */
        String DOLLAR               = "$";
        /**
         * single quotes "'"
         */
        String SINGLE_QUOTES        = "'";
        /**
         * double quotes "\""
         */
        String DOUBLE_QUOTES        = "\"";
    }

    interface Encoding {
        /**
         * encoding
         */
        String ISO88591 = "ISO-8859-1";
        String GB2312   = "GB2312";
        String GBK      = "GBK";
        String UTF8     = "UTF-8";
    }

    interface Timezone {
        String ASIA_SHANGHAI = "Asia/Shanghai";
    }

    interface Http {

        interface RequestMethod {
            /**
             * for request method
             */
            String PUT     = "PUT";
            String DELETE  = "DELETE";
            String GET     = "GET";
            String POST    = "POST";
            String HEAD    = "HEAD";
            String OPTIONS = "OPTIONS";
            String TRACE   = "TRACE";
        }

        interface HeaderKey {
            /**
             * for request,response header
             */
            String CONTENT_TYPE        = "Content-Type";
            String CONTENT_DISPOSITION = "Content-Disposition";
            String ACCEPT_CHARSET      = "Accept-Charset";
            String CONTENT_ENCODING    = "Content-Encoding";
        }

        interface ContentType {
            /**
             * for request,response content type
             */
            String TEXT_PLAIN                        = "text/plain";
            String APPLICATION_X_DOWNLOAD            = "application/x-download";
            String APPLICATION_ANDROID_PACKAGE       = "application/vnd.android.package-archive";
            String MULTIPART_FORM_DATA               = "multipart/form-data";
            String APPLICATION_OCTET_STREAM          = "application/octet-stream";
            String BINARY_OCTET_STREAM               = "binary/octet-stream";
            String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
        }

        interface StatusCode {

            int CONTINUE            = 100;
            int SWITCHING_PROTOCOLS = 101;
            int PROCESSING          = 102;

            int OK                            = 200;
            int CREATED                       = 201;
            int ACCEPTED                      = 202;
            int NON_AUTHORITATIVE_INFORMATION = 203;
            int NO_CONTENT                    = 204;
            int RESET_CONTENT                 = 205;
            int PARTIAL_CONTENT               = 206;
            int MULTI_STATUS                  = 207;

            int MULTIPLE_CHOICES   = 300;
            int MOVED_PERMANENTLY  = 301;
            int FOUND              = 302;
            int SEE_OTHER          = 303;
            int NOT_MODIFIED       = 304;
            int USE_PROXY          = 305;
            int SWITCH_PROXY       = 306;
            int TEMPORARY_REDIRECT = 307;

            int BAD_REQUEST          = 400;
            int UNAUTHORIZED         = 401;
            int PAYMENT_REQUIRED     = 402;
            int FORBIDDEN            = 403;
            int NOT_FOUND            = 404;
            int METHOD_NOT_ALLOWED   = 405;
            int NOT_ACCEPTABLE       = 406;
            int REQUEST_TIMEOUT      = 408;
            int CONFLICT             = 409;
            int GONE                 = 410;
            int LENGTH_REQUIRED      = 411;
            int PRECONDITION_FAILED  = 412;
            int REQUEST_URI_TOO_LONG = 414;
            int EXPECTATION_FAILED   = 417;
            int TOO_MANY_CONNECTIONS = 421;
            int UNPROCESSABLE_ENTITY = 422;
            int LOCKED               = 423;
            int FAILED_DEPENDENCY    = 424;
            int UNORDERED_COLLECTION = 425;
            int UPGRADE_REQUIRED     = 426;
            int RETRY_WITH           = 449;

            int INTERNAL_SERVER_ERROR        = 500;
            int NOT_IMPLEMENTED              = 501;
            int BAD_GATEWAY                  = 502;
            int SERVICE_UNAVAILABLE          = 503;
            int GATEWAY_TIMEOUT              = 504;
            int HTTP_VERSION_NOT_SUPPORTED   = 505;
            int VARIANT_ALSO_NEGOTIATES      = 506;
            int INSUFFICIENT_STORAGE         = 507;
            int LOOP_DETECTED                = 508;
            int BANDWIDTH_LIMIT_EXCEEDED     = 509;
            int NOT_EXTENDED                 = 510;
            int UNPARSEABLE_RESPONSE_HEADERS = 600;
        }
    }

    interface RequestScope {
        String SESSION = "session";
    }

    interface RequestParameter {
        String RETURN_URL = "returnUrl";
    }

    interface Database {
        String COLUMN_NAME_TOTAL = "TOTAL";

        interface MySql {
            /**
             * pagination
             */
            String PAGINATION = "LIMIT";
        }
    }

    interface Capacity {
        /**
         * bytes per kilobytes
         */
        int BYTES_PER_KB = 1024;

        /**
         * bytes per millionbytes
         */
        int BYTES_PER_MB = BYTES_PER_KB * BYTES_PER_KB;
    }

    interface Method {
        String PREFIX_SET = "set";
        String PREFIX_GET = "get";
        String PREFIX_IS  = "is";
        String GET_CLASS  = "getClass";
    }

    interface File {
        String CLASS = "class";
        String JPEG  = "jpeg";
        String JPG   = "jpg";
        String GIF   = "gif";
        String JAR   = "jar";
        String JAVA  = "java";
        String EXE   = "exe";
        String DEX   = "dex";
        String AIDL  = "aidl";
        String SO    = "so";
        String XML   = "xml";
        String CSV   = "csv";
        String TXT   = "txt";
        String APK   = "apk";
    }

    interface Protocol {
        String FILE = "file://";
        String HTTP = "http://";
        String FTP  = "ftp://";
    }
}

package org.mockserver.mappers;

import com.google.common.base.Strings;

/**
 * @author jamesdbloom
 */
public class ContentTypeMapper {

    public static boolean isBinary(String contentTypeHeader) {
        boolean binary = false;
        if (!Strings.isNullOrEmpty(contentTypeHeader)) {
            String contentType = contentTypeHeader.toLowerCase();
            boolean utf8Body = contentType.contains("utf-8")
                    || contentType.contains("utf8")
                    || contentType.contains("text")
                    || contentType.contains("javascript")
                    || contentType.contains("json")
                    || contentType.contains("ecmascript")
                    || contentType.contains("css")
                    || contentType.contains("csv")
                    || contentType.contains("html")
                    || contentType.contains("xhtml")
                    || contentType.contains("xml");
            if (!utf8Body) {
                binary = contentType.contains("ogg")
                        || contentType.contains("audio")
                        || contentType.contains("video")
                        || contentType.contains("image")
                        || contentType.contains("pdf")
                        || contentType.contains("postscript")
                        || contentType.contains("font")
                        || contentType.contains("woff")
                        || contentType.contains("model")
                        || contentType.contains("zip")
                        || contentType.contains("gzip")
                        || contentType.contains("nacl")
                        || contentType.contains("pnacl")
                        || contentType.contains("vnd")
                        || contentType.contains("application");
            }
        }
        return binary;
    }
}

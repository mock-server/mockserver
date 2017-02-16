package org.mockserver.mappers;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ContentTypeUtil {
    static Map<String, Integer> utf8ContentTypes = ImmutableMap.<String, Integer>builder().
            put("application/atom+xml", 1).
            put("application/ecmascript", 1).
            put("application/javascript", 1).
            put("application/json", 1).
            put("application/jsonml+json", 1).
            put("application/lost+xml", 1).
            put("application/wsdl+xml", 1).
            put("application/xaml+xml", 1).
            put("application/xhtml+xml", 1).
            put("application/xml", 1).
            put("application/xml-dtd", 1).
            put("application/xop+xml", 1).
            put("application/xslt+xml", 1).
            put("application/xspf+xml", 1).
            put("application/x-www-form-urlencoded", 1).
            put("image/svg+xml", 1).
            put("text/css", 1).
            put("text/csv", 1).
            put("text/html", 1).
            put("text/plain", 1).
            put("text/richtext", 1).
            put("text/sgml", 1).
            put("text/tab-separated-values", 1).
            put("text/x-fortran", 1).
            put("text/x-java-source", 1).build();
}

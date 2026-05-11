package org.mockserver.templates.engine.helpers;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class StringTemplateHelper {

    public String trim(String value) {
        return value != null ? value.trim() : "";
    }

    public String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ENGLISH) + value.substring(1);
    }

    public String uppercase(String value) {
        return value != null ? value.toUpperCase(Locale.ENGLISH) : "";
    }

    public String lowercase(String value) {
        return value != null ? value.toLowerCase(Locale.ENGLISH) : "";
    }

    public String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String urlDecode(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public String base64Encode(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public String base64Decode(String value) {
        if (value == null) {
            return "";
        }
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public String substringBefore(String value, String separator) {
        if (value == null || separator == null) {
            return value != null ? value : "";
        }
        int index = value.indexOf(separator);
        return index == -1 ? value : value.substring(0, index);
    }

    public String substringAfter(String value, String separator) {
        if (value == null || separator == null) {
            return "";
        }
        int index = value.indexOf(separator);
        return index == -1 ? "" : value.substring(index + separator.length());
    }

    public int length(String value) {
        return value != null ? value.length() : 0;
    }

    public boolean contains(String value, String search) {
        return value != null && search != null && value.contains(search);
    }

    public String replace(String value, String target, String replacement) {
        if (value == null || target == null) {
            return value != null ? value : "";
        }
        return value.replace(target, replacement != null ? replacement : "");
    }

    @Override
    public String toString() {
        return "StringTemplateHelper";
    }
}

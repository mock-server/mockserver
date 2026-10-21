package org.mockserver.codec;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Decodes a {@code multipart/form-data} request body into field-level
 * {@link Parameters} maps that can be matched the same way form parameters are.
 * <p>
 * Returns three multi-valued maps keyed by part field name:
 * <ul>
 *     <li>{@link DecodedMultipart#getFields() fields} — part values (text value or file bytes as a UTF-8 string)</li>
 *     <li>{@link DecodedMultipart#getFilenames() filenames} — filenames of file parts</li>
 *     <li>{@link DecodedMultipart#getPartContentTypes() partContentTypes} — part-level content types</li>
 * </ul>
 * Parsing uses Netty's {@link HttpPostMultipartRequestDecoder}; no additional
 * dependency is introduced.
 */
public class MultipartFormDataDecoder {

    // the limits Netty applies when none are given, kept so decode(...) behaves exactly as before
    private static final int NETTY_DEFAULT_MAX_FIELDS = 128;
    private static final int NETTY_DEFAULT_MAX_BUFFERED_BYTES = 1024;

    /**
     * Decodes the supplied body bytes using the boundary from the supplied
     * {@code Content-Type} header value.
     *
     * @param contentTypeHeader the full request Content-Type header (must contain a boundary)
     * @param body              the raw request body bytes
     * @return the decoded multipart parts, or {@code null} if the body is not multipart/form-data or cannot be parsed
     */
    public DecodedMultipart decode(String contentTypeHeader, byte[] body) {
        return withDecodedParts(contentTypeHeader, body, NETTY_DEFAULT_MAX_FIELDS, () -> null, datas -> {
            Map<String, List<String>> fields = new LinkedHashMap<>();
            Map<String, List<String>> filenames = new LinkedHashMap<>();
            Map<String, List<String>> partContentTypes = new LinkedHashMap<>();

            for (InterfaceHttpData data : datas) {
                if (data instanceof FileUpload) {
                    FileUpload fileUpload = (FileUpload) data;
                    Charset partCharset = fileUpload.getCharset() != null ? fileUpload.getCharset() : StandardCharsets.UTF_8;
                    add(fields, fileUpload.getName(), new String(fileUpload.get(), partCharset));
                    if (fileUpload.getFilename() != null) {
                        add(filenames, fileUpload.getName(), fileUpload.getFilename());
                    }
                    if (fileUpload.getContentType() != null) {
                        add(partContentTypes, fileUpload.getName(), fileUpload.getContentType());
                    }
                } else if (data instanceof Attribute) {
                    Attribute attribute = (Attribute) data;
                    add(fields, attribute.getName(), attribute.getValue());
                }
            }

            return new DecodedMultipart(toParameters(fields), toParameters(filenames), toParameters(partContentTypes));
        });
    }

    /**
     * Decodes the supplied body into its parts, in body order, with each part's raw bytes.
     *
     * @param maxParts the most parts accepted; a body with more parts is rejected rather than allocated
     * @return the decoded parts; no parts with {@code tooManyParts} set if the body has more than {@code maxParts}
     * parts; or {@code null} if the body is not multipart/form-data or is malformed
     */
    public DecodedParts decodeParts(String contentTypeHeader, byte[] body, int maxParts) {
        return withDecodedParts(contentTypeHeader, body, maxParts, () -> new DecodedParts(new ArrayList<>(), true), datas -> {
            List<DecodedPart> parts = new ArrayList<>(datas.size());
            for (InterfaceHttpData data : datas) {
                if (data instanceof FileUpload) {
                    FileUpload fileUpload = (FileUpload) data;
                    parts.add(new DecodedPart(fileUpload.getName(), fileUpload.getFilename(), fileUpload.getContentType(), fileUpload.getCharset(), fileUpload.get()));
                } else if (data instanceof Attribute) {
                    Attribute attribute = (Attribute) data;
                    parts.add(new DecodedPart(attribute.getName(), null, null, attribute.getCharset(), attribute.get()));
                }
            }
            return new DecodedParts(parts, false);
        });
    }

    private interface PartsReader<T> {
        T read(List<InterfaceHttpData> datas) throws Exception;
    }

    private static <T> T withDecodedParts(String contentTypeHeader, byte[] body, int maxFields, Supplier<T> tooManyFields, PartsReader<T> reader) {
        if (StringUtils.isBlank(contentTypeHeader) || !contentTypeHeader.toLowerCase().contains("multipart/form-data") || body == null) {
            return null;
        }
        // A purely in-memory factory does not track what it creates, so a part Netty was still filling when it stopped
        // (truncated body, too many parts) would never be released. A Mixed factory that never spills to disk tracks
        // every part for the request, and destroy() releases them all through cleanFiles().
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(Long.MAX_VALUE);
        HttpPostMultipartRequestDecoder decoder = null;
        HttpContent content = null;
        try {
            HttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            nettyRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, contentTypeHeader);
            nettyRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
            decoder = new HttpPostMultipartRequestDecoder(factory, nettyRequest, StandardCharsets.UTF_8, maxFields, NETTY_DEFAULT_MAX_BUFFERED_BYTES);
            if (!decoder.isMultipart()) {
                return null;
            }
            content = new DefaultLastHttpContent(Unpooled.wrappedBuffer(body));
            decoder.offer(content);
            return reader.read(decoder.getBodyHttpDatas());
        } catch (HttpPostRequestDecoder.TooManyFormFieldsException tooMany) {
            return tooManyFields.get();
        } catch (Throwable throwable) {
            return null;
        } finally {
            if (decoder != null) {
                try {
                    decoder.destroy();
                } catch (Throwable ignore) {
                    // best effort cleanup
                }
            }
            if (content != null && content.refCnt() > 0) {
                content.release();
            }
        }
    }

    private static void add(Map<String, List<String>> map, String key, String value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    private static Parameters toParameters(Map<String, List<String>> map) {
        List<Parameter> parameters = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            parameters.add(new Parameter(entry.getKey(), entry.getValue()));
        }
        return new Parameters(parameters);
    }

    /**
     * Immutable holder for the three field-level views of a decoded multipart body.
     */
    public static class DecodedMultipart {
        private final Parameters fields;
        private final Parameters filenames;
        private final Parameters partContentTypes;

        DecodedMultipart(Parameters fields, Parameters filenames, Parameters partContentTypes) {
            this.fields = fields;
            this.filenames = filenames;
            this.partContentTypes = partContentTypes;
        }

        public Parameters getFields() {
            return fields;
        }

        public Parameters getFilenames() {
            return filenames;
        }

        public Parameters getPartContentTypes() {
            return partContentTypes;
        }
    }

    /**
     * The parts of a body, or none with {@code tooManyParts} set when the body had more parts than were allowed.
     */
    public record DecodedParts(List<DecodedPart> parts, boolean tooManyParts) {
    }

    /**
     * One decoded part: its form field name, the filename and content type it declared (null when absent), its charset
     * (null when it declared none) and its raw bytes.
     */
    public static class DecodedPart {
        private final String name;
        private final String filename;
        private final String contentType;
        private final Charset charset;
        private final byte[] bytes;

        public DecodedPart(String name, String filename, String contentType, Charset charset, byte[] bytes) {
            this.name = name;
            this.filename = filename;
            this.contentType = contentType;
            this.charset = charset;
            this.bytes = bytes != null ? bytes : new byte[0];
        }

        public String getName() {
            return name;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public Charset getCharset() {
            return charset;
        }

        public byte[] getBytes() {
            return bytes;
        }
    }
}

package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.mockserver.model.*;
import org.mockserver.version.Version;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class HarConverter {

    private static final ObjectMapper HAR_OBJECT_MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT);

    public String serialize(List<LogEventRequestAndResponse> requestAndResponses) {
        HarLog harLog = new HarLog()
            .withCreator(
                new HarCreator()
                    .withName("MockServer")
                    .withVersion(Version.getVersion())
            )
            .withEntries(
                requestAndResponses.stream()
                    .map(this::convertEntry)
                    .collect(Collectors.toList())
            );

        Map<String, HarLog> wrapper = new LinkedHashMap<>();
        wrapper.put("log", harLog);

        try {
            return HAR_OBJECT_MAPPER.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize HAR", e);
        }
    }

    private HarEntry convertEntry(LogEventRequestAndResponse requestAndResponse) {
        HarEntry entry = new HarEntry()
            .withStartedDateTime(requestAndResponse.getTimestamp() != null ? requestAndResponse.getTimestamp() : "")
            .withTime(0)
            .withRequest(convertRequest(requestAndResponse.getHttpRequest()))
            .withResponse(convertResponse(requestAndResponse.getHttpResponse()));

        HttpRequest httpRequest = requestAndResponse.getHttpRequest();
        if (httpRequest != null) {
            if (isNotBlank(httpRequest.getRemoteAddress())) {
                String remoteAddress = httpRequest.getRemoteAddress();
                int slashIndex = remoteAddress.indexOf('/');
                if (slashIndex >= 0) {
                    remoteAddress = remoteAddress.substring(slashIndex + 1);
                }
                int colonIndex = remoteAddress.lastIndexOf(':');
                if (colonIndex > 0) {
                    entry.withServerIPAddress(remoteAddress.substring(0, colonIndex));
                } else {
                    entry.withServerIPAddress(remoteAddress);
                }
            } else if (httpRequest.getSocketAddress() != null && isNotBlank(httpRequest.getSocketAddress().getHost())) {
                entry.withServerIPAddress(httpRequest.getSocketAddress().getHost());
            }

            if (httpRequest.getStreamId() != null) {
                entry.withConnection(String.valueOf(httpRequest.getStreamId()));
            }
        }

        return entry;
    }

    private HarRequest convertRequest(HttpRequest httpRequest) {
        if (httpRequest == null) {
            return new HarRequest()
                .withMethod("")
                .withUrl("")
                .withHttpVersion("HTTP/1.1");
        }

        HarRequest harRequest = new HarRequest()
            .withMethod(httpRequest.getMethod("GET"))
            .withUrl(reconstructUrl(httpRequest))
            .withHttpVersion(convertProtocol(httpRequest.getProtocol()))
            .withHeaders(convertHeaders(httpRequest.getHeaderList()))
            .withCookies(convertRequestCookies(httpRequest.getCookieList()))
            .withQueryString(convertQueryString(httpRequest.getQueryStringParameterList()));

        Body<?> body = httpRequest.getBody();
        if (body != null) {
            harRequest.withPostData(convertRequestBody(body));
            byte[] rawBytes = body.getRawBytes();
            if (rawBytes != null) {
                harRequest.withBodySize(rawBytes.length);
            }
        }

        return harRequest;
    }

    private HarResponse convertResponse(HttpResponse httpResponse) {
        if (httpResponse == null) {
            return new HarResponse()
                .withStatus(0)
                .withStatusText("")
                .withHttpVersion("HTTP/1.1");
        }

        HarResponse harResponse = new HarResponse()
            .withStatus(httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 0)
            .withStatusText(httpResponse.getReasonPhrase() != null ? httpResponse.getReasonPhrase() : "")
            .withHttpVersion("HTTP/1.1")
            .withHeaders(convertHeaders(httpResponse.getHeaderList()))
            .withCookies(convertResponseCookies(httpResponse.getCookieList()))
            .withContent(convertResponseBody(httpResponse));

        String locationHeader = httpResponse.getFirstHeader("Location");
        if (isNotBlank(locationHeader)) {
            harResponse.withRedirectURL(locationHeader);
        }

        byte[] rawBytes = httpResponse.getBodyAsRawBytes();
        if (rawBytes != null && rawBytes.length > 0) {
            harResponse.withBodySize(rawBytes.length);
        }

        return harResponse;
    }

    @SuppressWarnings("rawtypes")
    String reconstructUrl(HttpRequest httpRequest) {
        StringBuilder url = new StringBuilder();

        boolean secure = Boolean.TRUE.equals(httpRequest.isSecure());
        String scheme = secure ? "https" : "http";
        url.append(scheme).append("://");

        String host = null;
        int port = secure ? 443 : 80;

        if (httpRequest.getSocketAddress() != null) {
            SocketAddress socketAddress = httpRequest.getSocketAddress();
            if (isNotBlank(socketAddress.getHost())) {
                host = socketAddress.getHost();
            }
            if (socketAddress.getPort() != null) {
                port = socketAddress.getPort();
            }
        }

        if (host == null) {
            String hostHeader = httpRequest.getFirstHeader("host");
            if (isNotBlank(hostHeader)) {
                String[] parts = HttpRequest.splitHostPort(hostHeader);
                host = parts[0];
                if (parts.length > 1) {
                    try {
                        port = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (host == null) {
            host = "localhost";
        }

        url.append(host);

        boolean defaultPort = (secure && port == 443) || (!secure && port == 80);
        if (!defaultPort) {
            url.append(':').append(port);
        }

        NottableString path = httpRequest.getPath();
        if (path != null && isNotBlank(path.getValue())) {
            if (!path.getValue().startsWith("/")) {
                url.append('/');
            }
            url.append(path.getValue());
        } else {
            url.append('/');
        }

        Parameters queryStringParameters = httpRequest.getQueryStringParameters();
        if (queryStringParameters != null && !queryStringParameters.isEmpty()) {
            url.append('?');
            List<Parameter> entries = queryStringParameters.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                Parameter param = entries.get(i);
                String name = param.getName().getValue();
                List<NottableString> values = param.getValues();
                for (int j = 0; j < values.size(); j++) {
                    if (i > 0 || j > 0) {
                        url.append('&');
                    }
                    url.append(urlEncode(name)).append('=').append(urlEncode(values.get(j).getValue()));
                }
            }
        }

        return url.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private String convertProtocol(Protocol protocol) {
        if (protocol == null) {
            return "HTTP/1.1";
        }
        switch (protocol) {
            case HTTP_2:
                return "HTTP/2";
            case HTTP_1_1:
            default:
                return "HTTP/1.1";
        }
    }

    private List<HarNameValuePair> convertHeaders(List<Header> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyList();
        }
        List<HarNameValuePair> result = new ArrayList<>();
        for (Header header : headers) {
            String name = header.getName().getValue();
            for (NottableString value : header.getValues()) {
                result.add(new HarNameValuePair(name, value.getValue()));
            }
        }
        return result;
    }

    private List<HarCookie> convertRequestCookies(List<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return Collections.emptyList();
        }
        return cookies.stream()
            .map(cookie -> new HarCookie(
                cookie.getName().getValue(),
                cookie.getValue().getValue()
            ))
            .collect(Collectors.toList());
    }

    private List<HarCookie> convertResponseCookies(List<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return Collections.emptyList();
        }
        return cookies.stream()
            .map(cookie -> new HarCookie(
                cookie.getName().getValue(),
                cookie.getValue().getValue()
            ))
            .collect(Collectors.toList());
    }

    private List<HarNameValuePair> convertQueryString(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyList();
        }
        List<HarNameValuePair> result = new ArrayList<>();
        for (Parameter param : parameters) {
            String name = param.getName().getValue();
            for (NottableString value : param.getValues()) {
                result.add(new HarNameValuePair(name, value.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private HarPostData convertRequestBody(Body body) {
        HarPostData postData = new HarPostData();

        String contentType = body.getContentType();
        postData.withMimeType(contentType != null ? contentType : "");

        if (body instanceof ParameterBody) {
            ParameterBody parameterBody = (ParameterBody) body;
            List<HarNameValuePair> params = new ArrayList<>();
            for (Parameter param : parameterBody.getValue().getEntries()) {
                String name = param.getName().getValue();
                for (NottableString value : param.getValues()) {
                    params.add(new HarNameValuePair(name, value.getValue()));
                }
            }
            postData.withParams(params);
        } else if (body instanceof BinaryBody) {
            byte[] bytes = ((BinaryBody) body).getRawBytes();
            if (bytes != null) {
                postData.withText(Base64.getEncoder().encodeToString(bytes));
            }
        } else {
            String bodyString = body.toString();
            if (bodyString != null) {
                postData.withText(bodyString);
            }
        }

        return postData;
    }

    private HarContent convertResponseBody(HttpResponse httpResponse) {
        HarContent content = new HarContent();
        BodyWithContentType<?> body = httpResponse.getBody();

        if (body == null) {
            content.withSize(0);
            return content;
        }

        String contentType = body.getContentType();
        content.withMimeType(contentType != null ? contentType : "");

        byte[] rawBytes = body.getRawBytes();
        content.withSize(rawBytes != null ? rawBytes.length : 0);

        if (body instanceof BinaryBody) {
            if (rawBytes != null && rawBytes.length > 0) {
                content.withText(Base64.getEncoder().encodeToString(rawBytes));
                content.withEncoding("base64");
            }
        } else {
            String bodyString = body.toString();
            if (bodyString != null) {
                content.withText(bodyString);
            }
        }

        return content;
    }
}

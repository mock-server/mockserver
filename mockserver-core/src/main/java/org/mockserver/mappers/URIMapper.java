package org.mockserver.mappers;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;
import org.mockserver.url.URLEncoder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class URIMapper {

    public static URI getURI(HttpRequest httpRequest) {
        try {
            URL fullURL = new URL(URLEncoder.encodeURL(httpRequest.getURL()));
            if (fullURL.getQuery() != null) {
                httpRequest.withQueryStringParameters(new QueryStringDecoder("?" + fullURL.getQuery()).parameters());
            }
            StringBuilder queryString = new StringBuilder();
            List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
            for (int i = 0; i < queryStringParameters.size(); i++) {
                Parameter parameter = queryStringParameters.get(i);
                if (parameter.getValues().isEmpty()) {
                    queryString.append(parameter.getName());
                    queryString.append('=');
                } else {
                    List<String> values = parameter.getValues();
                    for (int j = 0; j < values.size(); j++) {
                        String value = values.get(j);
                        queryString.append(parameter.getName());
                        queryString.append('=');
                        queryString.append(value);
                        if (j < (values.size() - 1)) {
                            queryString.append('&');
                        }
                    }
                }
                if (i < (queryStringParameters.size() - 1)) {
                    queryString.append('&');
                }
            }

            String scheme = fullURL.getProtocol();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Request scheme was \"" + scheme + "\" but HTTP and HTTPS schemes are support");
            }

            int port = fullURL.getPort();
            if (port == -1) {
                if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }
            String path = httpRequest.getPath();
            if (Strings.isNullOrEmpty(path)) {
               path = fullURL.getPath();
            }
            String query = queryString.toString();
            if (Strings.isNullOrEmpty(query)) {
                query = null;
            }
            return new URI(fullURL.getProtocol(), fullURL.getUserInfo(), fullURL.getHost(), port, path, query, null);
        } catch (URISyntaxException urise) {
            throw new IllegalArgumentException("URISyntaxException while parsing \"" + URLEncoder.encodeURL(httpRequest.getURL()) + "\"", urise);
        } catch (MalformedURLException murle) {
            throw new IllegalArgumentException("MalformedURLException while parsing \"" + URLEncoder.encodeURL(httpRequest.getURL()) + "\"", murle);
        }
    }
}

package org.mockserver.service.apacheclient;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.mockserver.model.Book;
import org.mockserver.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.net.ProxySelector;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceApacheHttpClient implements BookService {

    @Resource
    private Environment environment;
    private Integer port;
    private Integer proxyPort;
    private String host;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @PostConstruct
    private void initialise() {
        port = environment.getProperty("bookService.port", Integer.class);
        proxyPort = environment.getProperty("bookService.proxyPort", Integer.class);
        host = environment.getProperty("bookService.host", "localhost");
        objectMapper = createObjectMapper();
        httpClient = createHttpClient();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore failures
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        // relax parsing
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationConfig.Feature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // use arrays
        objectMapper.configure(DeserializationConfig.Feature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY);
        return objectMapper;
    }

    private HttpClient createHttpClient() {
        HttpHost httpHost = new HttpHost("localhost", proxyPort);
        DefaultProxyRoutePlanner defaultProxyRoutePlanner = new DefaultProxyRoutePlanner(httpHost);
        return HttpClients.custom().setRoutePlanner(defaultProxyRoutePlanner).build();
        // todo - need to support SOCKS protocol for this solution to work - jamesdbloom 12/01/2014
//        return HttpClients.custom().setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault())).build();
        // todo - or this solution - jamesdbloom 12/01/2014
//        return HttpClients.custom().build();

    }

    public Book[] getAllBooks() {
        try {
            HttpResponse response = httpClient.execute(new HttpGet(new URIBuilder()
                    .setScheme("http")
                    .setHost(host)
                    .setPort(port)
                    .setPath("/get_books")
                    .build()));
            return objectMapper.readValue(EntityUtils.toByteArray(response.getEntity()), Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception creating HttpClient", e);
        }
    }

    public Book getBook(String id) {
        try {
            HttpResponse response = httpClient.execute(new HttpGet(new URIBuilder()
                    .setScheme("http")
                    .setHost(host)
                    .setPort(port)
                    .setPath("/get_book")
                    .setParameter("id", id)
                    .build()));
            return objectMapper.readValue(EntityUtils.toByteArray(response.getEntity()), Book.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception creating HttpClient", e);
        }
    }
}

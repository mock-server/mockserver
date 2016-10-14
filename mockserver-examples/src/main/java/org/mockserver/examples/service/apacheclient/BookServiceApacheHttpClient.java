package org.mockserver.examples.service.apacheclient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.mockserver.examples.model.Book;
import org.mockserver.examples.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceApacheHttpClient implements BookService {

    @Resource
    private Environment environment;
    private Integer port;
    private String host;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @PostConstruct
    private void initialise() {
        port = environment.getProperty("bookService.port", Integer.class);
        host = environment.getProperty("bookService.host", "localhost");
        objectMapper = createObjectMapper();
        httpClient = createHttpClient();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // ignore failures
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        // relax parsing
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // use arrays
        objectMapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
        // remove empty values from JSON
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return objectMapper;
    }

    private HttpClient createHttpClient() {
        HttpHost httpHost = new HttpHost(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")));
        DefaultProxyRoutePlanner defaultProxyRoutePlanner = new DefaultProxyRoutePlanner(httpHost);
        return HttpClients.custom().setRoutePlanner(defaultProxyRoutePlanner).build();
    }

    public Book[] getAllBooks() {
        String responseBody = "";
        try {
            HttpResponse response = httpClient.execute(new HttpGet(new URIBuilder()
                    .setScheme("http")
                    .setHost(host)
                    .setPort(port)
                    .setPath("/get_books")
                    .build()));
            responseBody = new String(EntityUtils.toByteArray(response.getEntity()), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
        try {
            return objectMapper.readValue(responseBody, Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception parsing JSON response [" + responseBody + "]", e);
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
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

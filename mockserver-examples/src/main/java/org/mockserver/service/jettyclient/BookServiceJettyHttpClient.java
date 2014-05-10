package org.mockserver.service.jettyclient;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.mockserver.model.Book;
import org.mockserver.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceJettyHttpClient implements BookService {

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
        HttpClient httpClient = new HttpClient();
        try {
            if (Boolean.parseBoolean(System.getProperty("proxySet"))) {
                httpClient.setProxy(new Address(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort"))));
            }
            httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Exception creating HttpClient", e);
        }
        return httpClient;
    }

    @PreDestroy
    private void stopClient() {
        try {
            httpClient.stop();
        } catch (Exception e) {
            throw new RuntimeException("Exception stopping HttpClient", e);
        }
    }

    @Override
    public Book[] getAllBooks() {
        try {
            ContentExchange contentExchange = new ContentExchange(true);
            contentExchange.setMethod("GET");
            contentExchange.setURL("http://" + host + ":" + port + "/get_books");
            httpClient.send(contentExchange);

            if (contentExchange.waitForDone() == HttpExchange.STATUS_COMPLETED) {
                return objectMapper.readValue(contentExchange.getResponseContent(), Book[].class);
            } else {
                throw new RuntimeException("Exception making request to retrieve all books");
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    @Override
    public Book getBook(String id) {
        try {
            ContentExchange contentExchange = new ContentExchange(true);
            contentExchange.setMethod("GET");
            contentExchange.setURL("http://" + host + ":" + port + "/get_book" + "?id=" + id);
            httpClient.send(contentExchange);

            if (contentExchange.waitForDone() == HttpExchange.STATUS_COMPLETED) {
                return objectMapper.readValue(contentExchange.getResponseContent(), Book.class);
            } else {
                throw new RuntimeException("Exception making request to retrieve all books");
            }

        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

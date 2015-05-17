package org.mockserver.service.javaclient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.Book;
import org.mockserver.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceJavaHttpClient implements BookService {

    @Resource
    private Environment environment;
    private Integer port;
    private String host;
    private ObjectMapper objectMapper;

    @PostConstruct
    private void initialise() {
        port = environment.getProperty("bookService.port", Integer.class);
        host = environment.getProperty("bookService.host", "localhost");
        objectMapper = createObjectMapper();
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

    private HttpURLConnection sendGETRequest(String uri, InetSocketAddress serverAddress, final InetSocketAddress proxyAddress) throws IOException {
        URL url = new URL("http://" + serverAddress.getHostName() + ":" + serverAddress.getPort() + uri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.SOCKS, proxyAddress));
        connection.setRequestMethod("GET");
        return connection;
    }

    public Book[] getAllBooks() {
        InetSocketAddress proxyAddress = new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")));
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        try {
            HttpURLConnection connection = sendGETRequest("/get_books", serverAddress, proxyAddress);
            return objectMapper.readValue(connection.getInputStream(), Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    public Book getBook(String id) {
        InetSocketAddress proxyAddress = new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")));
        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        try {
            HttpURLConnection connection = sendGETRequest("/get_book?id=" + id, serverAddress, proxyAddress);
            return objectMapper.readValue(connection.getInputStream(), Book.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

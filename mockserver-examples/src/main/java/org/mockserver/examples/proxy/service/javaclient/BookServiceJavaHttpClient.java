package org.mockserver.examples.proxy.service.javaclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.examples.proxy.model.Book;
import org.mockserver.examples.proxy.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

import static org.mockserver.examples.proxy.json.ObjectMapperFactory.createObjectMapper;

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

    private HttpURLConnection sendRequestViaProxy(URL url) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort"))));
        return (HttpURLConnection) url.openConnection(proxy);
    }

    public Book[] getAllBooks() {
        try {
            HttpURLConnection connection = sendRequestViaProxy(new URL("http://" + host + ":" + port + "/get_books"));
            connection.setRequestMethod("GET");
            return objectMapper.readValue(connection.getInputStream(), Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    public Book getBook(String id) {
        try {
            HttpURLConnection connection = sendRequestViaProxy(new URL("http://" + host + ":" + port + "/get_book?id=" + id));
            connection.setRequestMethod("GET");
            return objectMapper.readValue(connection.getInputStream(), Book.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

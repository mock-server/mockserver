package org.mockserver.examples.service.googleclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.mockserver.examples.model.Book;
import org.mockserver.examples.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;

import static org.mockserver.examples.json.ObjectMapperFactory.createObjectMapper;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceGoogleHttpClient implements BookService {

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

    private HttpResponse sendRequestViaProxy(URL url, String method, @Nullable HttpContent content) throws IOException {
        ProxySelector defaultProxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return Arrays.asList(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    System.out.println("Connection could not be established to proxy at socket [" + sa + "]");
                    ioe.printStackTrace();
                }
            });
            HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
            return requestFactory.buildRequest(method, new GenericUrl(url), content).execute();
        } finally {
            ProxySelector.setDefault(defaultProxySelector);
        }
    }

    public Book[] getAllBooks() {
        try {
            HttpResponse httpResponse = sendRequestViaProxy(new URL("http://" + host + ":" + port + "/get_books"), HttpMethods.GET, null);
            return objectMapper.readValue(httpResponse.getContent(), Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    public Book getBook(String id) {
        try {
            HttpResponse httpResponse = sendRequestViaProxy(new URL("http://" + host + ":" + port + "/get_book?id=" + id), HttpMethods.GET, null);
            return objectMapper.readValue(httpResponse.getContent(), Book.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

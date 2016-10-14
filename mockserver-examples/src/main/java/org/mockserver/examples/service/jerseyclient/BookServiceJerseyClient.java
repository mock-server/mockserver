package org.mockserver.examples.service.jerseyclient;

import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.mockserver.examples.model.Book;
import org.mockserver.examples.service.BookService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

/**
 * @author jamesdbloom
 */
@Component
public class BookServiceJerseyClient implements BookService {

    @Resource
    private Environment environment;
    private Integer port;
    private Integer proxyPort;
    private String host;
    private Client client;

    @PostConstruct
    private void initialise() {
        port = environment.getProperty("bookService.port", Integer.class);
        proxyPort = environment.getProperty("bookService.proxyPort", Integer.class);
        host = environment.getProperty("bookService.host", "localhost");
        client = createHttpClient();
    }

    private Client createHttpClient() {
        return ClientBuilder.newClient(new ClientConfig()
                .register(new JacksonFeature())
                .connectorProvider(new ApacheConnectorProvider())
                .property(ClientProperties.PROXY_URI, "http://" + System.getProperty("http.proxyHost") + ":" + System.getProperty("http.proxyPort")));
    }

    public Book[] getAllBooks() {
        try {
            return client.target("http://" + host + ":" + port)
                    .path("get_books")
                    .queryParam("greeting", "Hi World!")
                    .request(MediaType.APPLICATION_JSON)
                    .get(Book[].class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve all books", e);
        }
    }

    public Book getBook(String id) {
        try {
            return client.target("http://localhost:" + port)
                    .path("get_book")
                    .queryParam("id", id)
                    .request(MediaType.APPLICATION_JSON)
                    .get(Book.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception making request to retrieve a book with id [" + id + "]", e);
        }
    }
}

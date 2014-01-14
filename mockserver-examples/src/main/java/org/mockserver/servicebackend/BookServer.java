package org.mockserver.servicebackend;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mockserver.model.Book;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
@Component
public class BookServer {

    private final Map<String, Book> booksDB = createBookData();
    private final ObjectMapper objectMapper = createObjectMapper();
    private Server server;
    private final int httpPort;

    public BookServer(int httpPort) {
        this.httpPort = httpPort;
    }

    public void startServer() {
        try {
            server = new Server();
            addServerConnector(server, httpPort, null);
            server.setHandler(new AbstractHandler() {
                public void handle(String target, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
                    String uri = httpServletRequest.getRequestURI();
                    if ("/get_books".equals(uri)) {
                        request.setHandled(true);
                        httpServletResponse.setStatus(200);
                        httpServletResponse.setHeader("Content-Type", "application/json");
                        httpServletResponse.getOutputStream().print(
                                objectMapper
                                        .writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(booksDB.values())
                        );
                    } else if ("/get_book".equals(uri)) {
                        request.setHandled(true);
                        httpServletResponse.setStatus(200);
                        httpServletResponse.setHeader("Content-Type", "application/json");
                        Book book = booksDB.get(request.getParameter("id"));
                        if (book != null) {
                            httpServletResponse.getOutputStream().print(
                                    objectMapper
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(book)
                            );
                        }

                    }
                }
            });
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Exception starting BookServer", e);
        }
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

    private Map<String, Book> createBookData() {
        Map<String, Book> booksDB = new HashMap<>();
        booksDB.put("1", new Book(1, "Xenophon's imperial fiction : on the education of Cyrus", "James Tatum", "0691067570", "1989"));
        booksDB.put("2", new Book(2, "You are here : personal geographies and other maps of the imagination", "Katharine A. Harmon", "1568984308", "2004"));
        booksDB.put("3", new Book(3, "You just don't understand : women and men in conversation", "Deborah Tannen", "0345372050", "1990"));
        booksDB.put("4", new Book(4, "XML for dummies", "Ed Tittel", "0764506927", "2000"));
        booksDB.put("5", new Book(5, "Your Safari Dragons: In Search of the Real Komodo Dragon", "Daniel White", "1595940146", "2005"));
        booksDB.put("6", new Book(6, "Zeus: A Journey Through Greece in the Footsteps of a God", "Tom Stone", "158234518X", "2008"));
        booksDB.put("7", new Book(7, "Zarafa: a giraffe's true story, from deep in Africa to the heart of Paris", "Michael Allin", "0802713394", "1998"));
        booksDB.put("8", new Book(8, "You Are Not a Gadget: A Manifesto", "Jaron Lanier", "0307269647", "2010"));
        return booksDB;
    }

    private void addServerConnector(Server server, int port, SslContextFactory sslContextFactory) throws Exception {
        ServerConnector serverConnector = new ServerConnector(server);
        if (sslContextFactory != null) {
            serverConnector = new ServerConnector(server, sslContextFactory);
        }
        serverConnector.setPort(port);
        server.addConnector(serverConnector);
    }

    public Map<String, Book> getBooksDB() {
        return booksDB;
    }

    public void stopServer() throws Exception {
        server.stop();
    }
}

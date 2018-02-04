package org.mockserver.examples.proxy.web.controller;

import org.junit.*;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.examples.proxy.model.Book;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;
import org.mockserver.socket.PortFactory;
import org.mockserver.examples.proxy.web.controller.pageobjects.BookPage;
import org.mockserver.examples.proxy.web.controller.pageobjects.BooksPage;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.Arrays;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author jamesdbloom
 */
public abstract class BooksPageIntegrationTest {

    static {
        MockServerLogger.setRootLogLevel("org.springframework");
        MockServerLogger.setRootLogLevel("org.eclipse");
    }
    private static ClientAndServer proxy;
    private ClientAndServer mockServer;
    @Resource
    private Environment environment;
    @Resource
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @BeforeClass
    public static void startProxy() {
        proxy = ClientAndServer.startClientAndServer();
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(proxy.getLocalPort()));
    }

    @AfterClass
    public static void stopProxy() {
        proxy.stop();
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }

    @Before
    public void setupFixture() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Before
    public void startMockServer() {
        mockServer = startClientAndServer(environment.getProperty("bookService.port", Integer.class));
        proxy.reset();
    }

    @After
    public void stopMockServer() {
        mockServer.stop();
    }

    @Test
    public void shouldLoadListOfBooks() throws Exception {
        // given
        mockServer
                .when(
                        request()
                                .withPath("/get_books")
                )
                .respond(
                        response()
                                .withHeaders(
                                        new Header(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_VALUE)
                                )
                                .withBody("" +
                                        "[" + NEW_LINE +
                                        "    {" + NEW_LINE +
                                        "        \"id\": \"1\"," + NEW_LINE +
                                        "        \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                                        "        \"author\": \"James Tatum\"," + NEW_LINE +
                                        "        \"isbn\": \"0691067570\"," + NEW_LINE +
                                        "        \"publicationDate\": \"1989\"" + NEW_LINE +
                                        "    }," + NEW_LINE +
                                        "    {" + NEW_LINE +
                                        "        \"id\": \"2\"," + NEW_LINE +
                                        "        \"title\": \"You are here : personal geographies and other maps of the imagination\"," + NEW_LINE +
                                        "        \"author\": \"Katharine A. Harmon\"," + NEW_LINE +
                                        "        \"isbn\": \"1568984308\"," + NEW_LINE +
                                        "        \"publicationDate\": \"2004\"" + NEW_LINE +
                                        "    }," + NEW_LINE +
                                        "    {" + NEW_LINE +
                                        "        \"id\": \"3\"," + NEW_LINE +
                                        "        \"title\": \"You just don't understand : women and men in conversation\"," + NEW_LINE +
                                        "        \"author\": \"Deborah Tannen\"," + NEW_LINE +
                                        "        \"isbn\": \"0345372050\"," + NEW_LINE +
                                        "        \"publicationDate\": \"1990\"" + NEW_LINE +
                                        "    }" +
                                        "]")
                );

        // when
        MvcResult response = mockMvc.perform(get("/books").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html; charset=utf-8"))
                .andReturn();

        // then
        new BooksPage(response).containsListOfBooks(Arrays.asList(
                new Book(1, "Xenophon's imperial fiction : on the education of Cyrus", "James Tatum", "0691067570", "1989"),
                new Book(2, "You are here : personal geographies and other maps of the imagination", "Katharine A. Harmon", "1568984308", "2004"),
                new Book(3, "You just don't understand : women and men in conversation", "Deborah Tannen", "0345372050", "1990")
        ));
        proxy.verify(
                request()
                        .withPath("/get_books"),
                exactly(1)
        );
    }

    @Test
    public void shouldLoadSingleBook() throws Exception {
        // given
        mockServer
                .when(
                        request()
                                .withPath("/get_book")
                                .withQueryStringParameter(
                                        new Parameter("id", "1")
                                )
                )
                .respond(
                        response()
                                .withHeaders(
                                        new Header(CONTENT_TYPE.toString(), "application/json")
                                )
                                .withBody("" +
                                        "{" + NEW_LINE +
                                        "    \"id\": \"1\"," + NEW_LINE +
                                        "    \"title\": \"Xenophon's imperial fiction : on the education of Cyrus\"," + NEW_LINE +
                                        "    \"author\": \"James Tatum\"," + NEW_LINE +
                                        "    \"isbn\": \"0691067570\"," + NEW_LINE +
                                        "    \"publicationDate\": \"1989\"" + NEW_LINE +
                                        "}")
                );

        // when
        MvcResult response = mockMvc.perform(get("/book/1").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html; charset=utf-8"))
                .andReturn();

        // then
        new BookPage(response).containsBook(new Book(1, "Xenophon's imperial fiction : on the education of Cyrus", "James Tatum", "0691067570", "1989"));
        proxy.verify(
                request()
                        .withPath("/get_book")
                        .withQueryStringParameter(
                                new Parameter("id", "1")
                        ),
                exactly(1)
        );
    }

}

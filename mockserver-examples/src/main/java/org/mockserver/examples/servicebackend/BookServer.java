package org.mockserver.examples.servicebackend;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.examples.json.ObjectMapperFactory;
import org.mockserver.examples.model.Book;
import org.mockserver.socket.SSLFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author jamesdbloom
 */
public class BookServer {

    private static ServerBootstrap serverBootstrap;
    private final Map<String, Book> booksDB = createBookData();
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final int httpPort;
    private final boolean secure;

    public BookServer(int httpPort, boolean secure) {
        this.httpPort = httpPort;
        this.secure = secure;
    }

    @PostConstruct
    public void startServer() throws InterruptedException {
        if (serverBootstrap == null) {
            try {
                serverBootstrap = new ServerBootstrap()
                        .group(new NioEventLoopGroup(1))
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();

                                // add HTTPS support
                                if (secure) {
                                    pipeline.addLast(new SslHandler(SSLFactory.createServerSSLEngine()));
                                }

                                // pipeline.addLast("logger", new LoggingHandler("BOOK_HANDLER"));
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new HttpContentDecompressor());
                                pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                                pipeline.addLast(new BookHandler());
                            }
                        });

            } catch (Exception e) {
                throw new RuntimeException("Exception starting BookServer", e);
            }
        }
        serverBootstrap.bind(httpPort);
        System.gc();
        TimeUnit.SECONDS.sleep(3);
    }

    private Map<String, Book> createBookData() {
        Map<String, Book> booksDB = new HashMap<String, Book>();
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

    public Map<String, Book> getBooksDB() {
        return booksDB;
    }

    @PreDestroy
    public void stopServer() throws Exception {

    }

    private class BookHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            FullHttpResponse response = null;
            if (request.getUri().startsWith("/get_books")) {
                response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                        Unpooled.wrappedBuffer(
                                objectMapper
                                        .writerWithDefaultPrettyPrinter()
                                        .writeValueAsBytes(booksDB.values())
                        )
                );
                response.headers().set(CONTENT_TYPE, "application/json");
                response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            } else if (request.getUri().startsWith("/get_book")) {
                List<String> id = new QueryStringDecoder(request.getUri()).parameters().get("id");
                if (id != null && !id.isEmpty()) {
                    Book book = booksDB.get(id.get(0));
                    if (book != null) {
                        response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                                Unpooled.wrappedBuffer(
                                        objectMapper
                                                .writerWithDefaultPrettyPrinter()
                                                .writeValueAsBytes(book)
                                )
                        );
                        response.headers().set(CONTENT_TYPE, "application/json");
                        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
                    }
                }
            }
            if (response == null) {
                response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
            }
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

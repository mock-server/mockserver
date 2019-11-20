package org.mockserver.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.dashboard.model.LogEntryDTO;
import org.mockserver.dashboard.serializers.LogEntryDTOSerializer;
import org.mockserver.dashboard.serializers.ThrowableSerializer;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.ui.MockServerLogListener;
import org.mockserver.ui.MockServerMatcherListener;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.HOST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DashboardWebSocketServerHandler extends ChannelInboundHandlerAdapter implements MockServerLogListener, MockServerMatcherListener {

    private static final Predicate<LogEntryDTO> requestLogPredicate = input
        -> input.getType() == RECEIVED_REQUEST;
    private static final Predicate<LogEntryDTO> requestResponseLogPredicate = input
        -> input.getType() == EXPECTATION_RESPONSE
        || input.getType() == EXPECTATION_NOT_MATCHED_RESPONSE
        || input.getType() == FORWARDED_REQUEST;
    private static final Predicate<LogEntryDTO> recordedExpectationLogPredicate = input
        -> input.getType() == FORWARDED_REQUEST;
    private static final AttributeKey<Boolean> CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET = AttributeKey.valueOf("CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET");
    private static final String UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI = "/_mockserver_ui_websocket";
    private static final int UI_UPDATE_ITEM_LIMIT = 50;
    private static ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private final HttpStateHandler httpStateHandler;
    private HttpRequestSerializer httpRequestSerializer;
    private WebSocketServerHandshaker handshaker;
    private CircularHashMap<ChannelHandlerContext, HttpRequest> clientRegistry = new CircularHashMap<>(100);
    private MockServerMatcher mockServerMatcher;
    private MockServerEventLog mockServerEventLog;
    private ThreadPoolExecutor scheduler;
    private ScheduledExecutorService throttleExecutorService;
    private Semaphore semaphore;

    public DashboardWebSocketServerHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        try {
            scheduler = new ThreadPoolExecutor(
                1,
                1,
                0L,
                SECONDS,
                new LinkedBlockingQueue<>(1),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy()
            );
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception creating scheduler " + throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (this.scheduler != null) {
            scheduler.shutdown();
        }
        if (this.throttleExecutorService != null) {
            throttleExecutorService.shutdownNow();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        boolean release = true;
        try {
            if (msg instanceof FullHttpRequest && ((FullHttpRequest) msg).uri().equals(UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI)) {
                upgradeChannel(ctx, (FullHttpRequest) msg);
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).set(true);
            } else if (ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).get() != null &&
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).get() &&
                msg instanceof WebSocketFrame) {
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void upgradeChannel(final ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        if (mockServerEventLog == null) {
            mockServerEventLog = httpStateHandler.getMockServerLog();
            mockServerEventLog.registerListener(this);
            mockServerMatcher = httpStateHandler.getMockServerMatcher();
            mockServerMatcher.registerListener(this);
        }
        handshaker = new WebSocketServerHandshakerFactory(
            "ws://" + httpRequest.headers().get(HOST) + UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI,
            null,
            true,
            Integer.MAX_VALUE
        ).newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(
                ctx.channel(),
                httpRequest,
                new DefaultHttpHeaders(),
                ctx.channel().newPromise()
            ).addListener((ChannelFutureListener) future -> clientRegistry.put(ctx, request()));
        }
        if (objectMapper == null) {
            objectMapper = ObjectMapperFactory.createObjectMapper(
                new LogEntryDTOSerializer(),
                new ThrowableSerializer()
            );
        }
        if (httpRequestSerializer == null) {
            httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
        }
        if (semaphore == null) {
            semaphore = new Semaphore(1);
        }
        if (throttleExecutorService == null) {
            throttleExecutorService = Executors.newScheduledThreadPool(1);
        }
        if (scheduler == null) {
            scheduler = new ThreadPoolExecutor(
                1,
                1,
                0L,
                SECONDS,
                new LinkedBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy()
            );
        }
        throttleExecutorService.scheduleAtFixedRate(() -> {
            if (semaphore.availablePermits() == 0) {
                semaphore.release(1);
            }
        }, 0, 1, SECONDS);
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain()).addListener((ChannelFutureListener) future -> clientRegistry.remove(ctx));
        } else if (frame instanceof TextWebSocketFrame) {
            try {
                HttpRequest httpRequest = httpRequestSerializer.deserialize(((TextWebSocketFrame) frame).text());
                clientRegistry.put(ctx, httpRequest);
                sendUpdate(httpRequest, ctx);
            } catch (IllegalArgumentException iae) {
                sendMessage(ctx, ImmutableMap.of("error", iae.getMessage()));
            }
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
        } else {
            throw new UnsupportedOperationException(frame.getClass().getName() + " frame types not supported");
        }
    }

    private void sendMessage(ChannelHandlerContext ctx, ImmutableMap<String, Object> message) {
        if (semaphore.tryAcquire()) {
            scheduler.submit(() -> {
                try {
                    ctx.writeAndFlush(new TextWebSocketFrame(
                        objectMapper.writeValueAsString(message)
                    ));
                } catch (JsonProcessingException jpe) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("Exception will serialising UI data " + jpe.getMessage())
                            .setThrowable(jpe)
                    );
                }
            });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("web socket server caught exception")
                    .setThrowable(cause)
            );
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (mockServerMatcher != null) {
            mockServerMatcher.unregisterListener(this);
        }
        if (mockServerEventLog != null) {
            mockServerEventLog.unregisterListener(this);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        for (Map.Entry<ChannelHandlerContext, HttpRequest> registryEntry : clientRegistry.entrySet()) {
            sendUpdate(registryEntry.getValue(), registryEntry.getKey());
        }
    }

    @Override
    public void updated(MockServerMatcher mockServerMatcher) {
        for (Map.Entry<ChannelHandlerContext, HttpRequest> registryEntry : clientRegistry.entrySet()) {
            sendUpdate(registryEntry.getValue(), registryEntry.getKey());
        }
    }

    private void sendUpdate(HttpRequest httpRequest, ChannelHandlerContext channelHandlerContext) {
        mockServerEventLog
            .retrieveLogEntriesInReverse(
                httpRequest,
                logEntry -> true,
                LogEntryDTO::new,
                reverseLogEventsStream -> {
                    List<Map<String, Object>> recordedExpectations = new ArrayList<>();
                    List<Map<String, Object>> recordedRequests = new ArrayList<>();
                    List<LogEntryDTO> recordedRequestResponses = new ArrayList<>();
                    List<LogEntryDTO> logMessages = new ArrayList<>();
                    reverseLogEventsStream
                        .forEach(logEntryDTO -> {
                            if (recordedExpectationLogPredicate.test(logEntryDTO)
                                && recordedExpectations.size() < UI_UPDATE_ITEM_LIMIT) {
                                recordedExpectations.add(
                                    ImmutableMap.of(
                                        "key", logEntryDTO.key(),
                                        "value", new Expectation(logEntryDTO.getHttpRequest(), Times.once(), TimeToLive.unlimited())
                                            .thenRespond(logEntryDTO.getHttpResponse())
                                    )
                                );
                            }
                            if (requestLogPredicate.test(logEntryDTO)
                                && recordedRequests.size() < UI_UPDATE_ITEM_LIMIT) {
                                for (HttpRequest request : logEntryDTO.getHttpRequests()) {
                                    recordedRequests.add(ImmutableMap.of(
                                        "key", logEntryDTO.key(),
                                        "value", request
                                    ));
                                }
                            }
                            if (requestResponseLogPredicate.test(logEntryDTO)
                                && recordedRequestResponses.size() < UI_UPDATE_ITEM_LIMIT) {
                                recordedRequestResponses.add(logEntryDTO);
                            }
                            if (logMessages.size() < UI_UPDATE_ITEM_LIMIT) {
                                logMessages.add(logEntryDTO);
                            }
                        });
                    sendMessage(channelHandlerContext, ImmutableMap.of(
                        "activeExpectations", mockServerMatcher
                            .retrieveActiveExpectations(httpRequest)
                            .stream()
                            .limit(UI_UPDATE_ITEM_LIMIT)
                            .map(expectation -> ImmutableMap.of(
                                "key", expectation.key(),
                                "value", expectation
                            ))
                            .collect(Collectors.toList()),
                        "recordedExpectations", recordedExpectations,
                        "recordedRequests", recordedRequests,
                        "recordedRequestResponses", recordedRequestResponses,
                        "logMessages", logMessages
                    ));
                }
            );
    }
}

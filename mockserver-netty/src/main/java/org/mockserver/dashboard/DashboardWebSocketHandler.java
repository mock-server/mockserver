package org.mockserver.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.model.DashboardLogEntryDTOGroup;
import org.mockserver.dashboard.serializers.*;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerLogListener;
import org.mockserver.mock.listeners.MockServerMatcherListener;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.ExpectationDTO;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.exception.ExceptionHandling.connectionClosedException;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DashboardWebSocketHandler extends ChannelInboundHandlerAdapter implements MockServerLogListener, MockServerMatcherListener {

    private static final Predicate<DashboardLogEntryDTO> recordedRequestsPredicate = input
        -> input.getType() == RECEIVED_REQUEST;
    private static final Predicate<DashboardLogEntryDTO> proxiedRequestsPredicate = input
        -> input.getType() == FORWARDED_REQUEST;
    private static final AttributeKey<Boolean> CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET = AttributeKey.valueOf("CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET");
    private static final String UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI = "/_mockserver_ui_websocket";
    private static final int UI_UPDATE_ITEM_LIMIT = 100;
    private static ObjectWriter objectWriter;
    private static ObjectMapper objectMapper;
    private final boolean prettyPrint;
    private final MockServerLogger mockServerLogger;
    private final boolean sslEnabledUpstream;
    private final HttpState httpState;
    private HttpRequestSerializer httpRequestSerializer;
    private WebSocketServerHandshaker handshaker;
    private Map<ChannelOutboundInvoker, HttpRequest> clientRegistry;
    private RequestMatchers requestMatchers;
    private MockServerEventLog mockServerEventLog;
    private ThreadPoolExecutor scheduler;
    private ScheduledExecutorService throttleExecutorService;
    private Semaphore semaphore;

    public DashboardWebSocketHandler(HttpState httpState, boolean sslEnabledUpstream, boolean prettyPrint) {
        this.httpState = httpState;
        this.mockServerLogger = httpState.getMockServerLogger();
        this.sslEnabledUpstream = sslEnabledUpstream;
        this.prettyPrint = prettyPrint;
    }

    @VisibleForTesting
    public Map<ChannelOutboundInvoker, HttpRequest> getClientRegistry() {
        if (clientRegistry == null) {
            clientRegistry = new CircularHashMap<>(100);
        }
        return clientRegistry;
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
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception creating scheduler " + throwable.getMessage())
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
        String webSocketURL = (sslEnabledUpstream ? "wss" : "ws") + "://" + httpRequest.headers().get(HOST) + UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI;
        if (MockServerLogger.isEnabled(Level.TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.TRACE)
                    .setMessageFormat("upgraded dashboard connection to support web sockets on url{}")
                    .setArguments(webSocketURL)
            );
        }
        handshaker = new WebSocketServerHandshakerFactory(
            webSocketURL,
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
            ).addListener((ChannelFutureListener) future -> getClientRegistry().put(ctx, request()));
        }
        registerListeners();
    }

    @VisibleForTesting
    protected DashboardWebSocketHandler registerListeners() {
        if (objectWriter == null) {
            objectMapper = ObjectMapperFactory.createObjectMapper(
                new DashboardLogEntryDTOSerializer(),
                new DashboardLogEntryDTOGroupSerializer(),
                new DescriptionSerializer(),
                new ThrowableSerializer()
            );
            if (prettyPrint) {
                objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
            } else {
                objectWriter = objectMapper.writer();
            }
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
        if (mockServerEventLog == null) {
            mockServerEventLog = httpState.getMockServerLog();
            mockServerEventLog.registerListener(this);
            requestMatchers = httpState.getRequestMatchers();
            requestMatchers.registerListener(this);
            scheduler.submit(() -> {
                try {
                    MILLISECONDS.sleep(100);
                } catch (InterruptedException ignore) {
                }
                // ensure any exception added during initialisation are caught
                updated(mockServerEventLog);
                updated(requestMatchers, null);
            });
        }
        return this;
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain()).addListener((ChannelFutureListener) future -> getClientRegistry().remove(ctx));
        } else if (frame instanceof TextWebSocketFrame) {
            try {
                HttpRequest httpRequest = httpRequestSerializer.deserialize(((TextWebSocketFrame) frame).text());
                getClientRegistry().put(ctx, httpRequest);
                sendUpdate(ctx, httpRequest);
            } catch (IllegalArgumentException iae) {
                sendMessage(ctx, null, ImmutableMap.of("error", iae.getMessage()), 2);
            }
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
        } else {
            throw new UnsupportedOperationException(frame.getClass().getName() + " frame types not supported");
        }
    }

    private void sendMessage(ChannelOutboundInvoker ctx, RequestDefinition httpRequest, ImmutableMap<String, Object> message, int retryCount) {
        if (semaphore.tryAcquire()) {
            scheduler.submit(() -> {
                try {
                    String text = objectWriter.writeValueAsString(message);
                    ctx.writeAndFlush(new TextWebSocketFrame(text));
                } catch (JsonProcessingException jpe) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception will serialising UI data " + jpe.getMessage())
                            .setThrowable(jpe)
                    );
                }
            });
        } else if (retryCount >= 0) {
            scheduler.submit(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(200);
                } catch (InterruptedException ignore) {
                }
                if (httpRequest != null) {
                    sendUpdate(ctx, httpRequest, retryCount - 1);
                } else {
                    sendMessage(ctx, null, message, retryCount - 1);
                }
            });

        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (connectionClosedException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("web socket server caught exception")
                    .setThrowable(cause)
            );
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (requestMatchers != null) {
            requestMatchers.unregisterListener(this);
        }
        if (mockServerEventLog != null) {
            mockServerEventLog.unregisterListener(this);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        for (Map.Entry<ChannelOutboundInvoker, HttpRequest> registryEntry : getClientRegistry().entrySet()) {
            sendUpdate(registryEntry.getKey(), registryEntry.getValue());
        }
    }

    @Override
    public void updated(RequestMatchers requestMatchers, MockServerMatcherNotifier.Cause cause) {
        for (Map.Entry<ChannelOutboundInvoker, HttpRequest> registryEntry : getClientRegistry().entrySet()) {
            sendUpdate(registryEntry.getKey(), registryEntry.getValue());
        }
    }

    @VisibleForTesting
    void sendUpdate(ChannelOutboundInvoker ctx, RequestDefinition httpRequest) {
        sendUpdate(ctx, httpRequest, 2);
    }

    private void sendUpdate(ChannelOutboundInvoker ctx, RequestDefinition httpRequest, int retryCount) {
        DescriptionProcessor activeExpectationsDescriptionProcessor = new DescriptionProcessor();
        DescriptionProcessor logMessagesDescriptionProcessor = new DescriptionProcessor();
        DescriptionProcessor recordedRequestsDescriptionProcessor = new DescriptionProcessor();
        DescriptionProcessor proxiedRequestsDescriptionProcessor = new DescriptionProcessor();
        mockServerEventLog
            .retrieveLogEntriesInReverseForUI(
                httpRequest,
                logEntry -> true,
                DashboardLogEntryDTO::new,
                reverseLogEventsStream -> {
                    List<ImmutableMap<String, Object>> activeExpectations = requestMatchers
                        .retrieveRequestMatchers(httpRequest)
                        .stream()
                        .limit(UI_UPDATE_ITEM_LIMIT)
                        .map(requestMatcher -> {
                            JsonNode expectationJsonNode = objectMapper.valueToTree(new ExpectationDTO(requestMatcher.getExpectation()));
                            if (requestMatcher.getExpectation().getHttpRequest() instanceof OpenAPIDefinition) {
                                JsonNode httpRequestJsonNode = expectationJsonNode.get("httpRequest");
                                if (httpRequestJsonNode instanceof ObjectNode) {
                                    ((ObjectNode) httpRequestJsonNode).set("requestMatchers", objectMapper.valueToTree(requestMatcher.getHttpRequests()));
                                }
                            }
                            return ImmutableMap.of(
                                "key", requestMatcher.getExpectation().getId(),
                                "description", activeExpectationsDescriptionProcessor.description(requestMatcher.getExpectation().getHttpRequest(), requestMatcher.getExpectation().getId()),
                                "value", expectationJsonNode
                            );
                        })
                        .collect(Collectors.toList());
                    List<Map<String, Object>> proxiedRequests = new LinkedList<>();
                    List<Map<String, Object>> recordedRequests = new LinkedList<>();
                    List<Object> logMessages = new LinkedList<>();
                    Map<String, DashboardLogEntryDTOGroup> logEntryGroups = new HashMap<>();
                    reverseLogEventsStream
                        .forEach(logEntryDTO -> {
                            if (logEntryDTO != null) {
                                if (logMessages.size() < UI_UPDATE_ITEM_LIMIT) {
                                    DashboardLogEntryDTO dashboardLogEntryDTO = logEntryDTO.setDescription(logMessagesDescriptionProcessor.description(logEntryDTO));
                                    if (isNotBlank(logEntryDTO.getCorrelationId())) {
                                        DashboardLogEntryDTOGroup logEntryGroup = logEntryGroups.get(logEntryDTO.getCorrelationId());
                                        if (logEntryGroup == null) {
                                            logEntryGroup = new DashboardLogEntryDTOGroup(logMessagesDescriptionProcessor);
                                            logEntryGroups.put(logEntryDTO.getCorrelationId(), logEntryGroup);
                                            logMessages.add(logEntryGroup);
                                        }
                                        logEntryGroup.getLogEntryDTOS().add(dashboardLogEntryDTO);
                                    } else {
                                        logMessages.add(dashboardLogEntryDTO);
                                    }
                                }
                                if (recordedRequestsPredicate.test(logEntryDTO) && recordedRequests.size() < UI_UPDATE_ITEM_LIMIT) {
                                    for (RequestDefinition request : logEntryDTO.getHttpRequests()) {
                                        if (request != null) {
                                            Map<String, Object> entry = new HashMap<>();
                                            entry.put("key", logEntryDTO.getId() + "_request");
                                            Description description = recordedRequestsDescriptionProcessor.description(logEntryDTO.getHttpRequest());
                                            if (description != null) {
                                                entry.put("description", description);
                                            }
                                            entry.put("value", request);
                                            recordedRequests.add(entry);
                                        }
                                    }
                                }
                                if (proxiedRequestsPredicate.test(logEntryDTO) && proxiedRequests.size() < UI_UPDATE_ITEM_LIMIT) {
                                    Map<String, Object> value = new HashMap<>();
                                    if (logEntryDTO.getHttpRequest() != null) {
                                        value.put("httpRequest", logEntryDTO.getHttpRequest());
                                    }
                                    if (logEntryDTO.getHttpResponse() != null) {
                                        value.put("httpResponse", logEntryDTO.getHttpResponse());
                                    }
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("key", logEntryDTO.getId() + "_proxied");
                                    Description description = proxiedRequestsDescriptionProcessor.description(logEntryDTO.getHttpRequest());
                                    if (description != null) {
                                        entry.put("description", description);
                                    }
                                    entry.put("value", value);
                                    if (!value.isEmpty()) {
                                        proxiedRequests.add(entry);
                                    }
                                }
                            }
                        });
                    sendMessage(ctx, httpRequest, ImmutableMap.of(
                        "logMessages", logMessages,
                        "activeExpectations", activeExpectations,
                        "recordedRequests", recordedRequests,
                        "proxiedRequests", proxiedRequests // reverse
                    ), retryCount);
                }
            );
    }

}

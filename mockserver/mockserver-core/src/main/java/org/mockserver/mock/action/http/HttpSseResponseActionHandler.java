package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpSseResponse;
import org.mockserver.model.SseEvent;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;

public class HttpSseResponseActionHandler {

    private final MockServerLogger mockServerLogger;
    private final Scheduler scheduler;

    public HttpSseResponseActionHandler(MockServerLogger mockServerLogger, Scheduler scheduler) {
        this.mockServerLogger = mockServerLogger;
        this.scheduler = scheduler;
    }

    public void handle(HttpSseResponse httpSseResponse, ChannelHandlerContext ctx, org.mockserver.model.HttpRequest request) {
        int statusCode = httpSseResponse.getStatusCode() != null ? httpSseResponse.getStatusCode() : 200;
        DefaultHttpResponse initialResponse = new DefaultHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(statusCode)
        );

        initialResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        initialResponse.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        initialResponse.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        initialResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");

        if (httpSseResponse.getHeaders() != null) {
            httpSseResponse.getHeaders().getEntries().forEach(header ->
                header.getValues().forEach(value ->
                    initialResponse.headers().add(header.getName().getValue(), value.getValue())
                )
            );
        }

        ctx.writeAndFlush(initialResponse);

        List<SseEvent> events = httpSseResponse.getEvents();
        if (events != null && !events.isEmpty()) {
            scheduleEvents(events, 0, ctx, httpSseResponse, request);
        } else {
            finishStream(ctx, httpSseResponse);
        }
    }

    private void scheduleEvents(List<SseEvent> events, int index, ChannelHandlerContext ctx, HttpSseResponse httpSseResponse, org.mockserver.model.HttpRequest request) {
        if (index >= events.size() || !ctx.channel().isActive()) {
            finishStream(ctx, httpSseResponse);
            return;
        }

        SseEvent event = events.get(index);
        Delay delay = event.getDelay();

        Runnable writeEvent = () -> {
            try {
                if (!ctx.channel().isActive()) {
                    return;
                }
                String sseData = formatSseEvent(event);
                DefaultHttpContent content = new DefaultHttpContent(
                    Unpooled.copiedBuffer(sseData, StandardCharsets.UTF_8)
                );
                ctx.writeAndFlush(content).addListener(future -> {
                    if (future.isSuccess()) {
                        if (MockServerLogger.isEnabled(Level.DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(EXPECTATION_RESPONSE)
                                    .setLogLevel(Level.DEBUG)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("sent SSE event {} of {} for request:{}")
                                    .setArguments(index + 1, events.size(), request)
                            );
                        }
                        scheduleEvents(events, index + 1, ctx, httpSseResponse, request);
                    } else {
                        if (MockServerLogger.isEnabled(Level.WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.WARN)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("async write failure for SSE event {} for request:{}")
                                    .setArguments(index + 1, request)
                                    .setThrowable((Throwable) future.cause())
                            );
                        }
                        finishStream(ctx, httpSseResponse);
                    }
                });
            } catch (Exception e) {
                if (MockServerLogger.isEnabled(Level.WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setHttpRequest(request)
                            .setMessageFormat("exception sending SSE event {} for request:{}")
                            .setArguments(index + 1, request)
                            .setThrowable(e)
                    );
                }
                finishStream(ctx, httpSseResponse);
            }
        };

        if (delay != null) {
            scheduler.schedule(writeEvent, false, delay);
        } else {
            writeEvent.run();
        }
    }

    private void finishStream(ChannelHandlerContext ctx, HttpSseResponse httpSseResponse) {
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(future -> {
                if (httpSseResponse.getCloseConnection() == null || httpSseResponse.getCloseConnection()) {
                    ctx.close();
                }
            });
        }
    }

    private String sanitizeSseFieldValue(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\n", "").replace("\r", "");
    }

    private String formatSseEvent(SseEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getId() != null) {
            sb.append("id: ").append(sanitizeSseFieldValue(event.getId())).append("\n");
        }
        if (event.getEvent() != null) {
            sb.append("event: ").append(sanitizeSseFieldValue(event.getEvent())).append("\n");
        }
        if (event.getRetry() != null) {
            sb.append("retry: ").append(event.getRetry()).append("\n");
        }
        if (event.getData() != null) {
            for (String line : event.getData().split("\n")) {
                sb.append("data: ").append(line).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}

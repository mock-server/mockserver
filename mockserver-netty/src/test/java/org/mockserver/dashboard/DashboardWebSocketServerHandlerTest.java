package org.mockserver.dashboard;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DashboardWebSocketServerHandlerTest {

    // TODO(jamesdbloom)
    //    - request filtering (i.e. by path and method)

    @Test
    public void shouldSerialiseBasicMessageOnlyEvents() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setMessageFormat("messagePartOne:{}messagePartTwo:{}")
                .setArguments("argumentOne", "argumentTwo"),
            new LogEntry()
                .setMessageFormat("messageFormat")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormat\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messagePartOne:\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0arg\"," + NEW_LINE +
            "        \"multiline\" : false," + NEW_LINE +
            "        \"argument\" : true," + NEW_LINE +
            "        \"value\" : \"argumentOne\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_1msg\"," + NEW_LINE +
            "        \"value\" : \"messagePartTwo:\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_1arg\"," + NEW_LINE +
            "        \"multiline\" : false," + NEW_LINE +
            "        \"argument\" : true," + NEW_LINE +
            "        \"value\" : \"argumentTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseEventsWithRequest() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setMessageFormat("messagePartOne:{}messagePartTwo:{}")
                .setArguments("argumentOne", "argumentTwo"),
            new LogEntry()
                .setHttpRequest(request("two"))
                .setMessageFormat("messageFormat")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormat\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messagePartOne:\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0arg\"," + NEW_LINE +
            "        \"multiline\" : false," + NEW_LINE +
            "        \"argument\" : true," + NEW_LINE +
            "        \"value\" : \"argumentOne\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_1msg\"," + NEW_LINE +
            "        \"value\" : \"messagePartTwo:\"" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_1arg\"," + NEW_LINE +
            "        \"multiline\" : false," + NEW_LINE +
            "        \"argument\" : true," + NEW_LINE +
            "        \"value\" : \"argumentTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseRollUpEventsWithCorrelationId() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        String logCorrelationId = UUID.randomUUID().toString();
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setHttpRequest(request("one").withLogCorrelationId(logCorrelationId))
                .setMessageFormat("messagePartOne:{}messagePartTwo:{}")
                .setArguments("argumentOne", "argumentTwo"),
            new LogEntry()
                .setHttpRequest(request("two").withLogCorrelationId(logCorrelationId))
                .setMessageFormat("messageFormat")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log_group\"," + NEW_LINE +
            "    \"group\" : {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"value\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"messageParts\" : [ {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "          \"value\" : \"messageFormat\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"messageParts\" : [ {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "          \"value\" : \"messagePartOne:\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(0).id() + "_0arg\"," + NEW_LINE +
            "          \"multiline\" : false," + NEW_LINE +
            "          \"argument\" : true," + NEW_LINE +
            "          \"value\" : \"argumentOne\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(0).id() + "_1msg\"," + NEW_LINE +
            "          \"value\" : \"messagePartTwo:\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(0).id() + "_1arg\"," + NEW_LINE +
            "          \"multiline\" : false," + NEW_LINE +
            "          \"argument\" : true," + NEW_LINE +
            "          \"value\" : \"argumentTwo\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseRollUpEventsWithSameCorrelationIdAndNotWarpEventsWithUniqueCorrelationId() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        String logCorrelationIdShared = UUID.randomUUID().toString();
        String logCorrelationIdOne = UUID.randomUUID().toString();
        String logCorrelationIdTwo = UUID.randomUUID().toString();
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setHttpRequest(request("one").withLogCorrelationId(logCorrelationIdShared))
                .setMessageFormat("messageFormatOne"),
            new LogEntry()
                .setHttpRequest(request("two").withLogCorrelationId(logCorrelationIdShared))
                .setMessageFormat("messageFormatTwo"),
            new LogEntry()
                .setHttpRequest(request("three").withLogCorrelationId(logCorrelationIdOne))
                .setMessageFormat("messageFormatThree"),
            new LogEntry()
                .setHttpRequest(request("four").withLogCorrelationId(logCorrelationIdTwo))
                .setMessageFormat("messageFormatFour")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(3).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(3).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatFour\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(2).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatThree\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log_group\"," + NEW_LINE +
            "    \"group\" : {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"value\" : [ {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"messageParts\" : [ {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "          \"value\" : \"messageFormatTwo\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "      \"value\" : {" + NEW_LINE +
            "        \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "        \"style\" : {" + NEW_LINE +
            "          \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "          \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "          \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "          \"overflow\" : \"auto\"," + NEW_LINE +
            "          \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "          \"paddingTop\" : \"4px\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"messageParts\" : [ {" + NEW_LINE +
            "          \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "          \"value\" : \"messageFormatOne\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseEventsWithoutFields() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setHttpRequest(request("one").withLogCorrelationId(UUID.randomUUID().toString())),
            new LogEntry()
                .setHttpRequest(request("two")),
            new LogEntry()
                .setMessageFormat("messageFormatTwo"),
            new LogEntry(),
            new LogEntry()
                .setThrowable(new RuntimeException())
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(4).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(4).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(3).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(2).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " INFO   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"style.whiteSpace\" : \"pre-wrap\"," + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(59,122,87)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseRecordedRequests() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("one"))
                .setMessageFormat("messageFormatOne"),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("two"))
                .setMessageFormat("messageFormatTwo"),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("three"))
                .setMessageFormat("messageFormatThree"),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("four"))
                .setMessageFormat("messageFormatFour")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(3).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(3).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatFour\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(2).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatThree\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatOne\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"recordedRequests\" : [ {" + NEW_LINE +
            "    \"description\" : \"   four\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"path\" : \"four\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_request\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"  three\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"path\" : \"three\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_request\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"    two\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"path\" : \"two\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_request\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"    one\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"path\" : \"one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_request\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseRecordedRequestsEventsWithoutFields() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setMessageFormat("messageFormatOne"),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("two")),
            new LogEntry()
                .setType(RECEIVED_REQUEST)
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " RECEIVED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(114,160,193)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatOne\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"recordedRequests\" : [ {" + NEW_LINE +
            "    \"description\" : \"  two\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"path\" : \"two\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_request\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseForwardedRequests() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("one"))
                .setHttpResponse(response("one"))
                .setMessageFormat("messageFormatOne"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("two"))
                .setHttpResponse(response("two"))
                .setMessageFormat("messageFormatTwo"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("three"))
                .setHttpResponse(response("three"))
                .setMessageFormat("messageFormatThree"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("four"))
                .setHttpResponse(response("four"))
                .setMessageFormat("messageFormatFour")
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(3).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(3).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatFour\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(2).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatThree\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatOne\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"proxiedRequests\" : [ {" + NEW_LINE +
            "    \"description\" : \"   four\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"four\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"four\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_proxied\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"  three\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"three\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"three\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_proxied\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"    two\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"two\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"two\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_proxied\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"    one\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"one\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"one\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_proxied\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseForwardedRequestsForEventsWithoutFields() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<LogEntry> logEntries = Arrays.asList(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("one"))
                .setMessageFormat("messageFormatOne"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpResponse(response("two"))
                .setMessageFormat("messageFormatTwo"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setMessageFormat("messageFormatThree"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
        );
        String renderedList = "{" + NEW_LINE +
            "  \"logMessages\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(3).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(3).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(2).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(2).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(2).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatThree\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(1).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(1).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatTwo\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_log\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"description\" : \"" + StringUtils.substringAfter(logEntries.get(0).getTimestamp(), "-") + " FORWARDED_REQUEST   \"," + NEW_LINE +
            "      \"style\" : {" + NEW_LINE +
            "        \"paddingBottom\" : \"4px\"," + NEW_LINE +
            "        \"whiteSpace\" : \"nowrap\"," + NEW_LINE +
            "        \"overflow\" : \"auto\"," + NEW_LINE +
            "        \"color\" : \"rgb(152, 208, 255)\"," + NEW_LINE +
            "        \"paddingTop\" : \"4px\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"messageParts\" : [ {" + NEW_LINE +
            "        \"key\" : \"" + logEntries.get(0).id() + "_0msg\"," + NEW_LINE +
            "        \"value\" : \"messageFormatOne\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"proxiedRequests\" : [ {" + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"two\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(1).id() + "_proxied\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"description\" : \"  one\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"one\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"key\" : \"" + logEntries.get(0).id() + "_proxied\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(logEntries, Collections.emptyList(), renderedList);
    }

    @Test
    public void shouldSerialiseExpectations() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        List<Expectation> expectations = Arrays.asList(
            new Expectation(request("one")).thenRespond(response("one")),
            new Expectation(request("two")).thenRespond(response("two")),
            new Expectation(request("three")).thenRespond(response("three"))
        );
        String renderedList = "" +
            "  \"activeExpectations\" : [ {" + NEW_LINE +
            "    \"key\" : \"" + expectations.get(0).getId() + "\"," + NEW_LINE +
            "    \"description\" : \"    one\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"id\" : \"" + expectations.get(0).getId() + "\"," + NEW_LINE +
            "      \"priority\" : 0," + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"one\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"one\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + expectations.get(1).getId() + "\"," + NEW_LINE +
            "    \"description\" : \"    two\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"id\" : \"" + expectations.get(1).getId() + "\"," + NEW_LINE +
            "      \"priority\" : 0," + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"two\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"two\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"key\" : \"" + expectations.get(2).getId() + "\"," + NEW_LINE +
            "    \"description\" : \"  three\"," + NEW_LINE +
            "    \"value\" : {" + NEW_LINE +
            "      \"id\" : \"" + expectations.get(2).getId() + "\"," + NEW_LINE +
            "      \"priority\" : 0," + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"three\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"three\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // then
        shouldRenderLogEntriesCorrectly(Collections.emptyList(), expectations, renderedList);
    }

    private void shouldRenderLogEntriesCorrectly(List<LogEntry> logEntries, List<Expectation> expectations, String renderedList) throws InterruptedException, ExecutionException, TimeoutException {
        // given
        MockServerLogger mockServerLogger = new MockServerLogger(DashboardWebSocketServerHandlerTest.class);
        Scheduler scheduler = new Scheduler(mockServerLogger);
        HttpState httpState = new HttpState(mockServerLogger, scheduler);
        DashboardWebSocketServerHandler handler =
            new DashboardWebSocketServerHandler(httpState, false, true)
                .registerListeners();
        MockChannelHandlerContext mockChannelHandlerContext = new MockChannelHandlerContext();
        handler.getClientRegistry().put(mockChannelHandlerContext, request());

        new Thread(() -> {
            MockServerEventLog mockServerEventLog = httpState.getMockServerLog();
            for (LogEntry logEntry : logEntries) {
                mockServerEventLog.add(logEntry);
            }
            RequestMatchers requestMatchers = httpState.getRequestMatchers();
            if (!expectations.isEmpty()) {
                requestMatchers.update(expectations.toArray(new Expectation[0]), MockServerMatcherNotifier.Cause.API);
            }
        }).start();
        SECONDS.sleep(2);

        // when
        handler.updated(new MockServerEventLog(mockServerLogger, scheduler, true));

        // then
        TextWebSocketFrame textWebSocketFrame = mockChannelHandlerContext.textWebSocketFrame;
        assertThat(textWebSocketFrame.text(), containsString(renderedList));
    }

    public static class MockChannelHandlerContext extends EmbeddedChannel {

        // can't use future as called mutiple times
        TextWebSocketFrame textWebSocketFrame;

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            if (msg instanceof TextWebSocketFrame) {
                textWebSocketFrame = (TextWebSocketFrame) msg;
            }
            return null;
        }
    }

}
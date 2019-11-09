package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.dashboard.model.LogEntryDTO;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogEntryDTOSerializerTest {

    @Test
    public void shouldSerialiseFullEvent() throws JsonProcessingException {
        // given
        LogEntry logEntry = new LogEntry()
            .setLogLevel(Level.WARN)
            .setEpochTime(System.currentTimeMillis())
            .setType(LogEntry.LogMessageType.TEMPLATE_GENERATED)
            .setHttpRequests(new HttpRequest[]{request("request_one"), request("request_two")})
            .setHttpResponse(response("response_one"))
            .setHttpError(error().withDropConnection(true))
            .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_one")))
            .setMessageFormat("some random {} formatted string {}")
            .setArguments("one", "two")
            .setThrowable(new RuntimeException("TEST_EXCEPTION"));

        // when
        String json = ObjectMapperFactory
            .createObjectMapper(
                new LogEntryDTOSerializer(),
                new ThrowableSerializer()
            )
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new LogEntryDTO(logEntry));

        // then
        assertThat(json, is("{" + NEW_LINE +
            "  \"key\" : \"" + logEntry.key() + "\"," + NEW_LINE +
            "  \"value\" : {" + NEW_LINE +
            "    \"logLevel\" : \"WARN\"," + NEW_LINE +
            "    \"timestamp\" : \"" + logEntry.getTimestamp() + "\"," + NEW_LINE +
            "    \"type\" : \"TEMPLATE_GENERATED\"," + NEW_LINE +
            "    \"httpRequests\" : [ {" + NEW_LINE +
            "      \"path\" : \"request_one\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"path\" : \"request_two\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"httpResponse\" : {" + NEW_LINE +
            "      \"statusCode\" : 200," + NEW_LINE +
            "      \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "      \"body\" : \"response_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"httpError\" : {" + NEW_LINE +
            "      \"dropConnection\" : true" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expectation\" : {" + NEW_LINE +
            "      \"httpRequest\" : {" + NEW_LINE +
            "        \"path\" : \"request_one\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"times\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"timeToLive\" : {" + NEW_LINE +
            "        \"unlimited\" : true" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\" : {" + NEW_LINE +
            "        \"statusCode\" : 200," + NEW_LINE +
            "        \"reasonPhrase\" : \"OK\"," + NEW_LINE +
            "        \"body\" : \"response_one\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"message\" : [ \"some random \", \"\", \"   one\", \"\", \" formatted string \", \"\", \"   two\" ]," + NEW_LINE +
            "    \"messageFormat\" : \"some random {} formatted string {}\"," + NEW_LINE +
            "    \"arguments\" : [ \"one\", \"two\" ]," + NEW_LINE +
            "    \"throwable\" : [ \"java.lang.RuntimeException: TEST_EXCEPTION\", \"\\tat org.mockserver.dashboard.serializers.LogEntryDTOSerializerTest.shouldSerialiseFullEvent(LogEntryDTOSerializerTest.java:33)\", \"\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\", \"\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\", \"\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\", \"\\tat java.lang.reflect.Method.invoke(Method.java:498)\", \"\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\", \"\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\", \"\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\", \"\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\", \"\\tat org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)\", \"\\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)\", \"\\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)\", \"\\tat org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)\", \"\\tat org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)\", \"\\tat org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)\", \"\\tat org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)\", \"\\tat org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)\", \"\\tat org.junit.runners.ParentRunner.run(ParentRunner.java:363)\", \"\\tat org.junit.runner.JUnitCore.run(JUnitCore.java:137)\", \"\\tat com.intellij.junit4.JUnit4IdeaTestRunner.startRunnerWithArgs(JUnit4IdeaTestRunner.java:68)\", \"\\tat com.intellij.rt.execution.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:47)\", \"\\tat com.intellij.rt.execution.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:242)\", \"\\tat com.intellij.rt.execution.junit.JUnitStarter.main(JUnitStarter.java:70)\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"));
    }
}

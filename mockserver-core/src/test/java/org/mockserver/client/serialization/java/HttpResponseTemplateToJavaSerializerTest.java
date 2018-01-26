package org.mockserver.client.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpTemplate;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        template(HttpTemplate.TemplateType.JAVASCRIPT)" + NEW_LINE +
                        "                .withTemplate(\"" +
                        StringEscapeUtils.escapeJava("if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                                "    return {" + NEW_LINE +
                                "        'statusCode': 200," + NEW_LINE +
                                "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                                "    };" + NEW_LINE +
                                "} else {" + NEW_LINE +
                                "    return {" + NEW_LINE +
                                "        'statusCode': 406," + NEW_LINE +
                                "        'body': request.body" + NEW_LINE +
                                "    };" + NEW_LINE +
                                "}"
                        ) +
                        "\")" + NEW_LINE +
                        "                .withDelay(new Delay(TimeUnit.SECONDS, 5))",
                new HttpTemplateToJavaSerializer().serialize(1,
                        new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT)
                                .withTemplate("if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
                                        "    return {" + NEW_LINE +
                                        "        'statusCode': 200," + NEW_LINE +
                                        "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
                                        "    };" + NEW_LINE +
                                        "} else {" + NEW_LINE +
                                        "    return {" + NEW_LINE +
                                        "        'statusCode': 406," + NEW_LINE +
                                        "        'body': request.body" + NEW_LINE +
                                        "    };" + NEW_LINE +
                                        "}"
                                )
                                .withDelay(new Delay(SECONDS, 5))
                )
        );
    }

}
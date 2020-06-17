/*
 *  Copyright 2017 SmartBear Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.mockserver.openapi.examples;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import org.mockserver.openapi.examples.models.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

public class JsonExampleDeserializer extends JsonDeserializer<Example> {

    @Override
    public Example deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        return createExample(node);
    }

    private Example createExample(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectExample obj = new ObjectExample();
            ObjectNode on = (ObjectNode) node;
            for (Iterator<Entry<String, JsonNode>> x = on.fields(); x.hasNext(); ) {
                Entry<String, JsonNode> i = x.next();
                String key = i.getKey();
                JsonNode value = i.getValue();
                obj.put(key, createExample(value));
            }
            return obj;
        } else if (node instanceof ArrayNode) {
            ArrayExample arr = new ArrayExample();
            ArrayNode an = (ArrayNode) node;
            for (JsonNode childNode : an) {
                arr.add(createExample(childNode));
            }
            return arr;
        } else if (node instanceof DoubleNode) {
            return new DoubleExample(node.doubleValue());
        } else if (node instanceof IntNode || node instanceof ShortNode) {
            return new IntegerExample(node.intValue());
        } else if (node instanceof FloatNode) {
            return new FloatExample(node.floatValue());
        } else if (node instanceof BigIntegerNode) {
            return new BigIntegerExample(node.bigIntegerValue());
        } else if (node instanceof DecimalNode) {
            return new DecimalExample(node.decimalValue());
        } else if (node instanceof LongNode) {
            return new LongExample(node.longValue());
        } else if (node instanceof BooleanNode) {
            return new BooleanExample(node.booleanValue());
        } else {
            return new StringExample(node.asText());
        }
    }
}
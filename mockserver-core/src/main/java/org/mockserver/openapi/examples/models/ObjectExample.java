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

package org.mockserver.openapi.examples.models;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ObjectExample extends AbstractExample {
    private Map<String, Example> values;

    public ObjectExample() {
        super.setTypeName("object");
    }

    public void put(String key, Example value) {
        if (values == null) {
            values = new LinkedHashMap<>();
        }
        values.put(key, value);
    }

    public void putAll(Map<String, Example> values) {
        for (String key : values.keySet()) {
            this.put(key, values.get(key));
        }
    }

    public Set<String> keySet() {
        if (values == null) {
            return new HashSet<>();
        }
        return values.keySet();
    }

    public Object get(String key) {
        if (values != null) {
            return values.get(key);
        }
        return null;
    }

    public String asString() {
        if (values == null) {
            return null;
        }
        return "NOT IMPLEMENTED";
    }

    public Map<String, Example> getValues() {
        return values;
    }

    public void setValues(Map<String, Example> values) {
        this.values = values;
    }
}

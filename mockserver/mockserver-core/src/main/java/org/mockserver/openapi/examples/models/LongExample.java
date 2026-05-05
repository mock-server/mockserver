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

/**
 * See: https://github.com/swagger-api/swagger-inflector
 */
public class LongExample extends AbstractExample {
    private Long value;

    public LongExample() {
        super.setTypeName("long");
    }

    public LongExample(long value) {
        this();
        this.value = value;
    }

    public String asString() {
        return String.valueOf(getValue());
    }

    public Long getValue() {
        return value != null ? value : 12345;
    }

    public void setValue(Long value) {
        this.value = value;
    }
}

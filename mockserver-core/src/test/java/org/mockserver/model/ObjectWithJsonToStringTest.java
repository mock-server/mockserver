package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ObjectWithJsonToStringTest {

    private class TestObject extends ObjectWithJsonToString {
        private String stringField = "stringField";
        private int intField = 100;

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }

        public int getIntField() {
            return intField;
        }

        public void setIntField(int intField) {
            this.intField = intField;
        }
    }

    @Test
    public void shouldConvertObjectToJSON() {
        assertThat(new TestObject().toString(), is("{" + System.getProperty("line.separator") +
                "  \"stringField\" : \"stringField\"," + System.getProperty("line.separator") +
                "  \"intField\" : 100" + System.getProperty("line.separator") +
                "}"));
    }

}
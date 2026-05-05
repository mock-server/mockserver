package org.mockserver.collections.nottablestring.hashmap;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.nottablestring.hashmap.NottableStringHashMapContainAllTest.TestScenario.*;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

public class NottableStringHashMapContainAllTest {

    // Test Pattern:
    // EMPTY
    // - empty
    // IDENTICAL
    // - identical keys and value
    // DIFFERENT CASE
    // - different case keys
    // - different case values
    // SUBSET
    // - subset values
    // NON MATCHING
    // - non matching keys
    // - non matching values
    // - non matching value in multi-value
    // - non matching keys and values
    // REGEX
    // - regex keys
    // - regex values
    // - regex keys and values
    // REGEX SUBSET
    // - regex subset values
    // REGEX NON MATCHING
    // - non matching regex keys
    // - non matching regex values
    // - non matching regex value in multi-value
    // - non matching regex keys and values
    // SCHEMA
    // - schema keys
    // - schema values
    // - schema keys and values
    // SCHEMA SUBSET
    // - schema subset values
    // SCHEMA NON MATCHING
    // - non matching schema keys
    // - non matching schema values
    // - non matching schema keys and values
    // NOTTED
    // - notted keys
    // - notted values
    // - notted value in multi-value
    // - notted key and value
    // NOTTED SUBSET
    // - subset notted value in multi-value
    // - subset notted key and value
    // NOTTED NOT MATCHING
    // - non matching notted keys
    // - non matching notted values
    // - non matching notted value in multi-value
    // - non matching notted keys and values
    // OPTIONAL
    // - optional keys
    // - optional keys with multi-value
    // OPTIONAL SUBSET
    // - optional key and value
    // - optional keys with multi-value
    // OPTIONAL NOT MATCHING
    // - non matching optional values
    // - non matching optional value in multi-value
    // CONTROL PLANE - REGEX
    // - control plane regex keys
    // - control plane regex values
    // - control plane regex keys and values
    // CONTROL PLANE - REGEX SUBSET
    // - control plane regex subset values
    // CONTROL PLANE - REGEX NOT MATCHING
    // - control plane non matching regex keys
    // - control plane non matching regex values
    // - control plane non matching regex keys and values
    // CONTROL PLANE - SCHEMA
    // - control plane schema values
    // CONTROL PLANE - SCHEMA NOT MATCHING
    // - control plane non matching schema keys
    // - control plane non matching schema values
    // - control plane non matching schema keys and values

    // EMPTY

    @Test
    public void shouldContainAllEmpty() {
        // can't match empty in reverse
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // IDENTICAL

    @Test
    public void shouldContainAllIdenticalKeysAndValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // DIFFERENT CASE

    @Test
    public void shouldContainAllDifferentCaseKeys() {
        shouldPassScenarios(
            passScenario(new String[]{
                "KEYOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "KEYOne", "valueOne",
                "keyTWO", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyTHREE", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllDifferentCaseValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueONE",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "VALUEOne",
                "keyTwo", "VALUETwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "VALUETwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // SUBSET

    @Test
    public void shouldContainAllSubsetKeysAndValues() {
        // can't match subsets in reverse
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // NON MATCHING

    @Test
    public void shouldContainAllNonMatchingKeys() {
        shouldPassScenarios(
            failScenario(new String[]{
                "notKeyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "notKeyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "notKeyOne", "valueOne",
                "keyTwo", "valueTwo",
                "notKeyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "notValueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "notValueTwo",
                "keyThree", "notValueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingKeysAndValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "notKeyOne", "notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "notKeyOne", "notValueOne",
                "notKeyTwo", "notValueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "notKeyTwo", "notValueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // REGEX

    @Test
    public void shouldContainAllIdenticalRegexKeys() {
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "key.*", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyO.*", "valueOne",
                "keyT.*", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyO.*", "valueOne",
                "keyT.*", "valueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalRegexValues() {
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "keyOne", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "value.*",
                "keyTwo", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalRegexKeysAndValues() {
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "key.*", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyO.*", "value.*",
                "keyT.*", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    // REGEX SUBSET

    @Test
    public void shouldContainAllSubsetRegexKeysAndValues() {
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "keyO.*", "valueO.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyO.*", "valueO.*",
                "keyT.*", "valueT.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // REGEX NON MATCHING

    @Test
    public void shouldContainAllNonMatchingRegexKeys() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyO[0-9]*", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyO[0-9]*", "valueOne",
                "keyT[0-9]*", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyT[0-9]*", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingRegexValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "value[0-9]*",
                "keyTwo", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "value[0-9]*",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingRegexKeysAndValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "key[0-9]*", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyO[0-9]*", "value[0-9]*",
                "keyT[0-9]*", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyT[0-9]*", "value[0-9]*",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // SCHEMA

    @Test
    public void shouldContainAllIdenticalSchemaKeys() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirection(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), string("valueOne")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), string("valueOne")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), string("valueOne")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), string("valueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalSchemaValues() {
        // can't match schema in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{string("keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalSchemaKeysAndValues() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirection(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")},
            })
        );
    }

    // SCHEMA SUBSET

    @Test
    public void shouldContainAllSubsetSchemaKeysAndValues() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirection(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
                new NottableString[]{string("keyThree"), string("valueThree")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyTw.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueTw.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
                new NottableString[]{string("keyThree"), string("valueThree")},
            })
        );
    }

    // SCHEMA NON MATCHING

    @Test
    public void shouldContainAllNotMatchingSchemaKeys() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirection(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKey.*\"}"), string("valueOne")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKeyO.*\"}"), string("valueOne")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKeyT.*\"}"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllNotMatchingSchemaValues() {
        // can't match schema in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"notNalueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"notValueO.*\"}")},
                new NottableString[]{string("keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"notValueT.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("?keyO[a-z]{2}"), schemaString("{\"type\": \"string\", \"pattern\": \"^valueO.*$\"}")},
                new NottableString[]{string("?keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"^valueT.*$\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notvalueOne")},
                new NottableString[]{string("keyTwo"), string("notvalueTwo")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("?keyO[a-z]{2}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{string("?keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
                new NottableString[]{string("keyTwo"), string("notValueTwo")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("?keyO[a-z]{2}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO[a-z]{1}$\"}")},
                new NottableString[]{string("?keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT[a-z]{2}$\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
                new NottableString[]{string("keyTwo"), string("notValueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllNotMatchingSchemaKeysAndValues() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirection(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKey.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"notValueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKeyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"notValueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"notKeyT.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"notValueT.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            })
        );
    }

    // NOTTED

    @Test
    public void shouldContainAllNottedKeys() {
        shouldPassScenarios(
            passScenario(new String[]{
                "!notKeyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            })
        );
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "!notKeyOne", "value.*",
                "!notKeyTwo", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "!notKeyTwo", "value.*",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
        // can't match multiple different values with matching key
        shouldPassScenarios(
            passScenario(new String[]{
                "!notKeyOne", "valueOne",
                "!notKeyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "!notKeyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNottedValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "!notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "!notValueOne",
                "keyTwo", "!notValueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "!notValueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNottedKeysAndValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "!notKeyOne", "!notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "!notKeyOne", "!notValueOne",
                "!notKeyTwo", "!notValueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "!notKeyTwo", "!notValueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // NOTTED SUBSET

    @Test
    public void shouldContainAllSubsetNottedKeysAndValues() {
        // can't match subsets in reverse
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "keyOne", "!notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "!notKeyOne", "!notValueOne",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "!notValueOne",
                "keyTwo", "!notValueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            passScenario(new String[]{
                "!notKeyOne", "!notValueOne",
                "!notKeyTwo", "!notValueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // NOTTED NOT MATCHING

    @Test
    public void shouldContainAllNonMatchingNottedKeys() {
        shouldPassScenarios(
            failScenario(new String[]{
                "!keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "!keyOne", "valueOne",
                "!keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "valueOne",
                "!keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingNottedValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "!valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "!valueOne",
                "keyTwo", "!valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "!valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingNottedKeysAndValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "!keyOne", "!valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "!keyOne", "valueOne",
                "keyTwo", "!valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "!keyTwo", "valueTwo",
                "keyThree", "!valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    // OPTIONAL

    @Test
    public void shouldContainAllOptionalKeys() {
        shouldPassScenarios(
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "?keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "?keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    // OPTIONAL SUBSET

    @Test
    public void shouldContainAllOptionalSubsetKeysAndValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "?keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "?keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            })
        );
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyThree", "valueThree",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "?keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyFour", "valueFour",
            })
        );
        // can't match subsets in reverse
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "?keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
                "keyFour", "valueFour",
            })
        );
    }

    // OPTIONAL NOT MATCHING

    @Test
    public void shouldContainAllNonMatchingOptionalValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "?keyOne", "valueOne",
            }, new String[]{
                "keyOne", "notValueOne",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "notValueOne",
                "keyTwo", "notValueTwo",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "notValueOne",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyTwo", "notValueTwo",
            }),
            failScenario(new String[]{
                "?keyO[a-z]{2}", "valueOne",
                "?keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "notValueOne",
                "keyTwo", "notValueTwo",
            })
        );
    }

    // CONTROL PLANE - REGEX

    @Test
    public void shouldContainAllIdenticalRegexKeysForControlPlane() {
        shouldPassScenariosControlPlane(
            passScenario(new String[]{
                "key.*", "valueOne",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyO.*", "valueOne",
                "keyT.*", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalRegexValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            passScenario(new String[]{
                "keyOne", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyOne", "value.*",
                "keyTwo", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllIdenticalRegexKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            passScenario(new String[]{
                "key.*", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
            }),
            passScenario(new String[]{
                "keyO.*", "value.*",
                "keyT.*", "value.*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            })
        );
    }

    // CONTROL PLANE - REGEX SUBSET

    @Test
    public void shouldContainAllSubsetRegexKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlaneSingleDirection(
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "keyO.*", "valueO.*",
                "keyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyO.*", "valueO.*",
                "keyT.*", "valueT.*",
                "keyThree", "valueThree",
            })
        );
    }

    // CONTROL PLANE - REGEX NOT MATCHING

    @Test
    public void shouldContainAllNonMatchingRegexKeysForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "key.*", "valueOne",
            }, new String[]{
                "notKeyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyO.*", "valueOne",
                "keyT.*", "valueTwo",
            }, new String[]{
                "notKeyOne", "valueOne",
                "notKeyTwo", "valueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingRegexValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "keyOne", "value.*",
            }, new String[]{
                "keyOne", "notValueOne",
            }),
            failScenario(new String[]{
                "keyOne", "value.*",
                "keyTwo", "value.*",
            }, new String[]{
                "keyOne", "notValueOne",
                "keyTwo", "notValueTwo",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingRegexKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "key.*", "value.*",
            }, new String[]{
                "keyOne", "notValueOne",
            }),
            failScenario(new String[]{
                "keyO.*", "value.*",
                "keyT.*", "value.*",
            }, new String[]{
                "keyOne", "notValueOne",
                "keyTwo", "notValueTwo",
            })
        );
    }

    // CONTROL PLANE - SCHEMA

    @Test
    public void shouldContainAllIdenticalSchemaValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
                new NottableString[]{string("keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            })
        );
    }

    // CONTROL PLANE - SCHEMA SUBSET

    @Test
    public void shouldContainAllSubsetSchemaKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlaneSingleDirection(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
            }, new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")},
                new NottableString[]{string("keyThree"), string("valueThree")},
            })
        );
    }

    // CONTROL PLANE - SCHEMA NOT MATCHING

    @Test
    public void shouldContainAllNonMatchingSchemaKeysForControlPlane() {
        shouldPassScenariosControlPlane(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), string("valueOne")},
            }, new NottableString[][]{
                new NottableString[]{string("notKeyOne"), string("valueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), string("valueOne")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"), string("valueTwo")},
            }, new NottableString[][]{
                new NottableString[]{string("notKeyOne"), string("valueOne")},
                new NottableString[]{string("notKeyTwo"), string("valueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingSchemaValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{string("keyOne"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
                new NottableString[]{string("keyTwo"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
                new NottableString[]{string("keyTwo"), string("notValueTwo")},
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingSchemaKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
            }),
            failSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"key.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"value.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("notValueOne")},
                new NottableString[]{string("keyTwo"), string("notValueTwo")},
            })
        );
    }

    public static class TestScenario {

        final NottableString[][] matcher;
        final NottableString[][] matched;
        final boolean result;

        public static TestScenario passScenario(String[] matcher, String[] matched) {
            return new TestScenario(matcher, matched, true);
        }

        public static TestScenario failScenario(String[] matcher, String[] matched) {
            return new TestScenario(matcher, matched, false);
        }

        public static TestScenario passSchemaScenario(NottableString[][] matcher, NottableString[][] matched) {
            return new TestScenario(matcher, matched, true);
        }

        public static TestScenario failSchemaScenario(NottableString[][] matcher, NottableString[][] matched) {
            return new TestScenario(matcher, matched, false);
        }

        public TestScenario(String[] matcher, String[] matched, boolean result) {
            this.matcher = groupAndConvert(matcher);
            this.matched = groupAndConvert(matched);
            this.result = result;
        }

        private NottableString[][] groupAndConvert(String[] strings) {
            Map<String, String> groupedValues = new LinkedHashMap<>();
            for (int i = 0; i < strings.length - 1; i += 2) {
                if (groupedValues.containsKey(strings[i])) {
                    throw new IllegalArgumentException("HashMaps only have single values per key");
                } else {
                    groupedValues.put(strings[i], strings[i + 1]);
                }
            }
            return groupedValues
                .entrySet()
                .stream()
                .map(mapEntry -> new NottableString[]{
                    string(mapEntry.getKey()),
                    string(mapEntry.getValue())
                })
                .toArray(NottableString[][]::new);
        }

        public TestScenario(NottableString[][] matcher, NottableString[][] matched, boolean result) {
            this.matcher = matcher;
            this.matched = matched;
            this.result = result;
        }

    }

    void shouldPassScenarios(TestScenario... testScenarios) {
        shouldPassScenarios(true, false, testScenarios, false);
    }

    void shouldPassScenariosSingleDirection(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, testScenarios, false);
    }

    void shouldPassScenariosControlPlane(TestScenario... testScenarios) {
        shouldPassScenarios(true, true, testScenarios, false);
        shouldPassScenarios(false, false, testScenarios, false);
    }

    void shouldPassScenariosControlPlaneSingleDirection(TestScenario... testScenarios) {
        shouldPassScenarios(false, true, testScenarios, false);
        shouldPassScenarios(false, false, testScenarios, true);
    }


    void shouldPassScenarios(boolean bothDirectionsSubSet, boolean controlPlane, TestScenario[] testScenarios, boolean reverseResult) {
        for (TestScenario testScenario : testScenarios) {
            // given - sub set
            NottableStringHashMap matcherForSubSet = new NottableStringHashMap(new MockServerLogger(), controlPlane, testScenario.matcher);
            NottableStringHashMap matchedForSubSet = new NottableStringHashMap(new MockServerLogger(), controlPlane, testScenario.matched);

            // then
            bidirectionMatch(bothDirectionsSubSet, testScenario, matcherForSubSet, matchedForSubSet, reverseResult != testScenario.result, controlPlane);
        }
    }

    private void bidirectionMatch(boolean bothDirections, TestScenario testScenario, NottableStringHashMap matcher, NottableStringHashMap matched, boolean result, boolean controlPlane) {
        try {
            assertThat(matched.containsAll(null, null, matcher), is(result));
        } catch (Throwable throwable) {
            System.out.println("expected " + (controlPlane ? "control plane " : "") + "matcher: " + doubleArrayToString(testScenario.matcher) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matched));
            throw throwable;
        }
        if (bothDirections) {
            try {
                assertThat(matcher.containsAll(null, null, matched), is(result));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + (controlPlane ? "control plane " : "") + "matcher: " + doubleArrayToString(testScenario.matched) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        } else if (result) {
            // only do not match in reverse for single directory when matches in non-reverse
            try {
                assertThat(matcher.containsAll(null, null, matched), is(false));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + (controlPlane ? "control plane " : "") + "matcher: " + doubleArrayToString(testScenario.matched) + " to not match matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        }
    }

    private String doubleArrayToString(NottableString[][] nottableStrings) {
        return Arrays.toString(Arrays.stream(nottableStrings).map(Arrays::toString).toArray());
    }

}
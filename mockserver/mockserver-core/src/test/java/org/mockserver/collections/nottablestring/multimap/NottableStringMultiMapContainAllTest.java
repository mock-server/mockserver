package org.mockserver.collections.nottablestring.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.nottablestring.multimap.NottableStringMultiMapContainAllTest.TestScenario.*;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

public class NottableStringMultiMapContainAllTest {

    // Test Pattern:
    // EMPTY
    // - empty
    // IDENTICAL
    // - identical keys and value
    // - identical keys and multi-values
    // DIFFERENT CASE
    // - different case keys
    // - different case values
    // - different case keys and multi-values
    // SUBSET
    // - subset values
    // - subset multi-values
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
    // - regex subset multi-values
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
    // - schema subset multi-values
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
    // - control plane regex subset multi-values
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
    // CONTROL PLANE - NOTTED (matching both notted and unnotted values, i.e. clear a notted value with a notted value)
    // - control plane notted keys - TODO
    // - control plane notted values - TODO
    // - control plane notted keys and values - TODO

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

    @Test
    public void shouldContainAllIdenticalKeysAndMultiValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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

    @Test
    public void shouldContainAllDifferentCaseKeysAndMultiValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "KEYOne", "valueOne_ONE",
                "KEYOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyONE", "valueOne_ONE",
                "keyONE", "valueOne_Two",
                "keyTWO", "valueTwo_ONE",
                "keyTWO", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTWO", "valueTwo_ONE",
                "keyTWO", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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

    @Test
    public void shouldContainAllSubsetMultiValues() {
        // can't match subsets in reverse
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirectionSubSetOnly(
            passScenario(new String[]{
                "keyOne", "valueOne_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyTwo", "valueTwo_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyThree", "valueThree_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            })
        );
        // can't match subsets in reverse
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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
    public void shouldContainAllNonMatchingValueInMultiValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "notValueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            failScenario(new String[]{
                "keyOne", "notValueOne_One",
                "keyOne", "notValueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "notValueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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
                "key.*", "valueOne",
                "key.*", "valueTwo",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "key.*", "valueOne",
                "key.*", "valueTwo",
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
                "key.*", "value.*",
                "key.*", "value.*",
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

    @Test
    public void shouldContainAllSubsetRegexKeysAndMultiValues() {
        // can't match regex in reverse (without control plane)
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirectionSubSetOnly(
            passScenario(new String[]{
                "keyO.*", "valueO.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyO.*", "valueOne_O.*",
                "keyO.*", "valueOne_Tw.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyOne", "valueOne_Three",
            }),
            passScenario(new String[]{
                "keyO.*", "valueOne_O.*",
                "keyO.*", "valueOne_Tw.*",
                "keyT.*", "valueT.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyOne", "valueOne_Three",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyTwo", "valueTwo_Three",
            }),
            passScenario(new String[]{
                "keyO.*", "valueOne_O.*",
                "keyO.*", "valueOne_T.*",
                "keyT.*", "valueTwo_O.*",
                "keyT.*", "valueTwo_T.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyTwo", "valueTwo_Three",
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
    public void shouldContainAllNonMatchingRegexValueInMultiValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "keyOne", "value[0-9]*",
                "keyOne", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "value[0-9]*",
                "keyTwo", "value[0-9]*",
                "keyTwo", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "value[0-9]*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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
        shouldPassScenariosSingleDirectionSubSetOnly(
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
    public void shouldContainAllIdenticalSchemaValuesForArraySchema() {
        // given - matching pattern
        NottableStringMultiMap matcherForMatchingKey = new NottableStringMultiMap(
            new MockServerLogger(),
            false,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), schemaString("{\"type\": \"array\", \"items\": {\"type\": \"string\", \"pattern\": \"value.*\"}}")}
        );
        NottableStringMultiMap matched = new NottableStringMultiMap(
            new MockServerLogger(),
            false,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), string("valueOne"), string("valueTwo"), string("valueThree")}
        );

        // then
        assertThat(matched.containsAll(null, null, matcherForMatchingKey), is(true));

        // given - not matching pattern
        matcherForMatchingKey = new NottableStringMultiMap(
            new MockServerLogger(),
            false,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), schemaString("{\"type\": \"array\", \"items\": {\"type\": \"string\", \"pattern\": \"valueO.*\"}}")}
        );
        matched = new NottableStringMultiMap(
            new MockServerLogger(),
            false,
            KeyMatchStyle.MATCHING_KEY,
            new NottableString[]{string("keyOne"), string("valueOne"), string("valueTwo"), string("valueThree")}
        );

        // then
        assertThat(matched.containsAll(null, null, matcherForMatchingKey), is(false));
    }

    @Test
    public void shouldContainAllIdenticalSchemaKeysAndValues() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirectionSubSetOnly(
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
        shouldPassScenariosSingleDirectionSubSetOnly(
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

    @Test
    public void shouldContainAllSubsetSchemaKeysAndMultiValues() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirectionSubSetOnly(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne_One"), string("valueOne_Two")},
                new NottableString[]{string("keyTwo"), string("valueTwo")},
                new NottableString[]{string("keyThree"), string("valueThree")},
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueO.*\"}")},
                new NottableString[]{schemaString("{\"type\": \"string\", \"pattern\": \"keyTw.*\"}"), schemaString("{\"type\": \"string\", \"pattern\": \"valueTw.*\"}")},
            }, new NottableString[][]{
                new NottableString[]{string("keyOne"), string("valueOne_One"), string("valueOne_Two")},
                new NottableString[]{string("keyTwo"), string("valueTwo_One"), string("valueTwo_Two")},
                new NottableString[]{string("keyThree"), string("valueThree")},
            })
        );
    }

    // SCHEMA NON MATCHING

    @Test
    public void shouldContainAllNotMatchingSchemaKeys() {
        // can't match schema in reverse (without control plane)
        // can't match schema by matching keys (without control plane)
        shouldPassScenariosSingleDirectionSubSetOnly(
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
        shouldPassScenariosSingleDirectionSubSetOnly(
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
//        // can't match regex in reverse (without control plane)
//        shouldPassScenariosSingleDirection(
//            passScenario(new String[]{
//                "!notKeyOne", "value.*",
//                "!notKeyTwo", "value.*",
//            }, new String[]{
//                "keyOne", "valueOne",
//                "keyTwo", "valueTwo",
//            }),
//            passScenario(new String[]{
//                "keyOne", "valueOne",
//                "!notKeyTwo", "value.*",
//                "keyThree", "valueThree",
//            }, new String[]{
//                "keyOne", "valueOne",
//                "keyTwo", "valueTwo",
//                "keyThree", "valueThree",
//            })
//        );
//        // can't match multiple different values with matching key
//        shouldPassScenariosSubSetOnly(
//            passScenario(new String[]{
//                "!notKeyOne", "valueOne",
//                "!notKeyTwo", "valueTwo",
//            }, new String[]{
//                "keyOne", "valueOne",
//                "keyTwo", "valueTwo",
//            }),
//            passScenario(new String[]{
//                "keyOne", "valueOne",
//                "!notKeyTwo", "valueTwo",
//                "keyThree", "valueThree",
//            }, new String[]{
//                "keyOne", "valueOne",
//                "keyTwo", "valueTwo",
//                "keyThree", "valueThree",
//            })
//        );
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
    public void shouldContainAllNottedKeysAndMultiValues() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "!notValueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "!notValueOne_One",
                "keyOne", "!notValueOne_Two",
                "keyTwo", "!notValueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "!notValueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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
        // can't match subsets in reverse
        // matching key still matches in reverse (but subset doesn't) so only subset
        shouldPassScenariosSingleDirectionSubSetOnly(passScenario(new String[]{
            "!keyOne", "!notValueOne",
        }, new String[]{
            "notKeyOne", "valueOne",
            "notKeyOne", "valueOne",
        }));
    }

    // NOTTED SUBSET

    @Test
    public void shouldContainAllSubsetNottedKeysAndValues() {
        // can't match subsets in reverse
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirectionSubSetOnly(
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

    @Test
    public void shouldContainAllSubsetNottedKeysAndMultiValues() {
        // can't match subsets in reverse
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirectionSubSetOnly(
            passScenario(new String[]{
                "keyOne", "!notValueOne_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "!notValueOne_One",
                "keyTwo", "!notValueTwo_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "!notValueOne_One",
                "keyOne", "!notValueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyThree", "!notValueThree_One",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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
        shouldPassScenariosSubSetOnly(
            failScenario(new String[]{
                "keyOne", "valueOne",
                "!keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            failScenario(new String[]{
                "!keyOne", ".*",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
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
    public void shouldContainAllNonMatchingNottedKeysAndMultiValues() {
        // can't match multiple different values with matching key
        shouldPassScenariosSubSetOnly(
            failScenario(new String[]{
                "keyOne", "!valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            failScenario(new String[]{
                "keyOne", "!valueOne_One",
                "keyOne", "!valueOne_Two",
                "keyTwo", "!valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "!valueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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

    @Test
    public void shouldContainAllOptionalKeysAndMultiValues() {
        // can't match regex in reverse (without control plane)
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "?keyOne", "valueOne.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne.*",
                "?keyTwo", "valueTwo.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            })
        );
        shouldPassScenarios(
            passScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "valueOne_Two",
                "?keyTwo", "valueTwo_One",
                "?keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
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

    @Test
    public void shouldContainAllOptionalSubsetKeysAndMultiValues() {
        // can't match regex in reverse (without control plane)
        // can't match subsets in reverse
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "?keyOne", "valueOne.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne.*",
                "?keyTwo", "valueTwo.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }),
            passScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "valueOne_Two",
                "?keyTwo", "valueTwo_One",
                "?keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
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

    @Test
    public void shouldContainAllNonMatchingOptionalMultiValues() {
        shouldPassScenarios(
            failScenario(new String[]{
                "?keyOne", "valueOne.*",
            }, new String[]{
                "keyOne", "notValueOne_One",
                "keyOne", "notValueOne_Two",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "notValueOne_Two",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne.*",
                "?keyTwo", "valueTwo.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "notValueTwo_One",
                "keyTwo", "notValueTwo_Two",
            }),
            failScenario(new String[]{
                "?keyOne", "valueOne_One",
                "?keyOne", "notValueOne_Two",
                "?keyTwo", "valueTwo_One",
                "?keyTwo", "notValueTwo_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
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
                "key.*", "value.*",
                "key.*", "value.*",
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

    @Test
    public void shouldContainAllSubsetRegexKeysAndMultiValuesForControlPlane() {
        // can't match multiple different values with matching key
        shouldPassScenariosControlPlaneSingleDirectionSubSetOnly(
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyO.*", "valueOne_O.*",
                "keyO.*", "valueOne_Tw.*",
                "keyO.*", "valueOne_Th.*",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyO.*", "valueOne_O.*",
                "keyO.*", "valueOne_Tw.*",
                "keyO.*", "valueOne_Th.*",
                "keyT.*", "valueT.*",
                "keyT.*", "valueT.*",
                "keyT.*", "valueT.*",
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
                "key.*", "value.*",
                "key.*", "value.*",
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

    @Test
    public void shouldContainAllSubsetSchemaKeysAndMultiValuesForControlPlane() {
        // can't match multiple different values with matching key
        shouldPassScenariosControlPlaneSingleDirectionSubSetOnly(
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{
                    string("keyOne"),
                    string("valueOne_One"),
                    string("valueOne_Two")
                },
            }, new NottableString[][]{
                new NottableString[]{
                    schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_O.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_Tw.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_Th.*\"}")
                },
            }),
            passSchemaScenario(new NottableString[][]{
                new NottableString[]{
                    string("keyOne"),
                    string("valueOne_One"),
                    string("valueOne_Two")
                },
                new NottableString[]{
                    string("keyTwo"),
                    string("valueTwo_One"),
                    string("valueTwo_Two")
                },
            }, new NottableString[][]{
                new NottableString[]{
                    schemaString("{\"type\": \"string\", \"pattern\": \"keyO.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_O.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_Tw.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueOne_Th.*\"}")
                },
                new NottableString[]{
                    schemaString("{\"type\": \"string\", \"pattern\": \"keyT.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}"),
                    schemaString("{\"type\": \"string\", \"pattern\": \"valueT.*\"}")
                },
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

    // CONTROL PLANE - NOTTED

    @Test
    public void shouldContainAllNottedKeysForControlPlane() {
        shouldPassScenarios(
//            passScenario(new String[]{
//                "!notKeyOne", "valueOne",
//            }, new String[]{
//                "!notKeyOne", "valueOne",
//            }),
//            passScenario(new String[]{
//                "!notKeyOne", "valueOne",
//                "!notKeyTwo", "valueTwo",
//            }, new String[]{
//                "!notKeyOne", "valueOne",
//                "!notKeyTwo", "valueTwo",
//            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "!notKeyOne", "valueOne",
            })
        );
        // can't match multiple different values with matching key
        shouldPassScenariosSubSetOnly(
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "!notKeyOne", "valueOne",
                "!notKeyTwo", "valueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "!notKeyTwo", "valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNottedValuesForControlPlane() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "keyOne", "!notValueOne",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "!notValueOne",
                "keyTwo", "!notValueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "!notValueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNottedKeysAndMultiValuesForControlPlane() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "!notValueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "!notValueOne_One",
                "keyOne", "!notValueOne_Two",
                "keyTwo", "!notValueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "!notValueThree_One",
                "keyThree", "valueThree_Two",
            })
        );
    }

    @Test
    public void shouldContainAllNottedKeysAndValuesForControlPlane() {
        shouldPassScenarios(
            passScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "!notKeyOne", "!notValueOne",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "!notKeyOne", "!notValueOne",
                "!notKeyTwo", "!notValueTwo",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "!notKeyTwo", "!notValueTwo",
                "keyThree", "valueThree",
            })
        );
        // can't match subsets in reverse
        // matching key still matches in reverse (but subset doesn't) so only subset
        shouldPassScenariosSingleDirectionSubSetOnly(passScenario(new String[]{
            "notKeyOne", "valueOne",
        }, new String[]{
            "!keyOne", "!notValueOne",
            "!notKeyTwo", "!notValueTwo",
        }));
    }

    // CONTROL PLANE - NOTTED NOT MATCHING

    @Test
    public void shouldContainAllNonMatchingNottedKeysForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "!keyOne", "valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "!keyOne", "valueOne",
                "!keyTwo", "valueTwo",
            })
        );
        shouldPassScenariosControlPlaneSubSetOnly(
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "!keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "!keyOne", ".*",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingNottedValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "keyOne", "!valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "keyOne", "!valueOne",
                "keyTwo", "!valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "keyTwo", "!valueTwo",
                "keyThree", "valueThree",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingNottedKeysAndMultiValuesForControlPlane() {
        // can't match multiple different values with matching key
        shouldPassScenariosControlPlaneSubSetOnly(
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "!valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }, new String[]{
                "keyOne", "!valueOne_One",
                "keyOne", "!valueOne_Two",
                "keyTwo", "!valueTwo_One",
                "keyTwo", "valueTwo_Two",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "valueThree_One",
                "keyThree", "valueThree_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
                "keyThree", "!valueThree_One",
                "keyThree", "valueThree_Two",
            })
        );
    }

    @Test
    public void shouldContainAllNonMatchingNottedKeysAndValuesForControlPlane() {
        shouldPassScenariosControlPlane(
            failScenario(new String[]{
                "keyOne", "valueOne",
            }, new String[]{
                "!keyOne", "!valueOne",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
            }, new String[]{
                "!keyOne", "valueOne",
                "keyTwo", "!valueTwo",
            }),
            failScenario(new String[]{
                "keyOne", "valueOne",
                "keyTwo", "valueTwo",
                "keyThree", "valueThree",
            }, new String[]{
                "keyOne", "valueOne",
                "!keyTwo", "valueTwo",
                "keyThree", "!valueThree",
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
            Map<String, List<String>> groupedValues = new LinkedHashMap<>();
            for (int i = 0; i < strings.length - 1; i += 2) {
                if (groupedValues.containsKey(strings[i])) {
                    groupedValues.get(strings[i]).add(strings[i + 1]);
                } else {
                    groupedValues.put(strings[i], new ArrayList<>(Collections.singletonList(strings[i + 1])));
                }
            }
            return groupedValues
                .entrySet()
                .stream()
                .map(mapEntry -> {
                    NottableString[] nottableStringEntry = new NottableString[mapEntry.getValue().size() + 1];
                    nottableStringEntry[0] = string(mapEntry.getKey());
                    for (int i = 0; i < mapEntry.getValue().size(); i++) {
                        nottableStringEntry[i + 1] = string(mapEntry.getValue().get(i));
                    }
                    return nottableStringEntry;
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
        shouldPassScenarios(true, true, false, true, testScenarios, false);
    }

    void shouldPassScenariosSingleDirection(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, false, true, testScenarios, false);
    }

    void shouldPassScenariosSubSetOnly(TestScenario... testScenarios) {
        shouldPassScenarios(true, true, false, false, testScenarios, false);
    }

    void shouldPassScenariosSingleDirectionSubSetOnly(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, false, false, testScenarios, false);
    }

    void shouldPassScenariosControlPlane(TestScenario... testScenarios) {
        shouldPassScenarios(true, true, true, true, testScenarios, false);
        shouldPassScenarios(false, false, false, true, testScenarios, false);
    }

    void shouldPassScenariosControlPlaneSingleDirection(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, true, true, testScenarios, false);
        shouldPassScenarios(false, false, false, true, testScenarios, true);
    }

    void shouldPassScenariosControlPlaneSubSetOnly(TestScenario... testScenarios) {
        shouldPassScenarios(true, true, true, false, testScenarios, false);
        shouldPassScenarios(false, false, false, false, testScenarios, false);
    }

    void shouldPassScenariosControlPlaneSingleDirectionSubSetOnly(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, true, false, testScenarios, false);
        shouldPassScenarios(false, false, false, false, testScenarios, true);
    }

    void shouldPassScenarios(boolean bothDirectionsSubSet, boolean bothDirectionsMatchingKey, boolean controlPlane, boolean includeMatchingKey, TestScenario[] testScenarios, boolean reverseResult) {
        for (TestScenario testScenario : testScenarios) {
            // given - sub set
            KeyMatchStyle subSet = KeyMatchStyle.SUB_SET;
            NottableStringMultiMap matcherForSubSet = new NottableStringMultiMap(new MockServerLogger(), controlPlane, subSet, testScenario.matcher);
            NottableStringMultiMap matchedForSubSet = new NottableStringMultiMap(new MockServerLogger(), controlPlane, subSet, testScenario.matched);

            // then
            bidirectionMatch(bothDirectionsSubSet, testScenario, matcherForSubSet, matchedForSubSet, reverseResult != testScenario.result, subSet, controlPlane);

            if (includeMatchingKey) {
                // given - matching key
                KeyMatchStyle matchingKey = KeyMatchStyle.MATCHING_KEY;
                NottableStringMultiMap matcherForMatchingKey = new NottableStringMultiMap(new MockServerLogger(), controlPlane, matchingKey, testScenario.matcher);
                NottableStringMultiMap matchedForMatchingKey = new NottableStringMultiMap(new MockServerLogger(), controlPlane, matchingKey, testScenario.matched);

                // then
                bidirectionMatch(bothDirectionsMatchingKey, testScenario, matcherForMatchingKey, matchedForMatchingKey, reverseResult != testScenario.result, matchingKey, controlPlane);
            }
        }
    }

    private void bidirectionMatch(boolean bothDirections, TestScenario testScenario, NottableStringMultiMap matcher, NottableStringMultiMap matched, boolean result, KeyMatchStyle keyMatchStyle, boolean controlPlane) {
        try {
            assertThat(matched.containsAll(null, null, matcher), is(result));
        } catch (Throwable throwable) {
            System.out.println("expected " + (controlPlane ? "control plane " : "") + keyMatchStyle + " matcher: " + doubleArrayToString(testScenario.matcher) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matched));
            throw throwable;
        }
        if (bothDirections) {
            try {
                assertThat(matcher.containsAll(null, null, matched), is(result));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + (controlPlane ? "control plane " : "") + keyMatchStyle + " matcher: " + doubleArrayToString(testScenario.matched) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        } else if (result) {
            // only do not match in reverse for single directory when matches in non-reverse
            try {
                assertThat(matcher.containsAll(null, null, matched), is(false));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + (controlPlane ? "control plane " : "") + keyMatchStyle + " matcher: " + doubleArrayToString(testScenario.matched) + " to not match matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        }
    }

    private String doubleArrayToString(NottableString[][] nottableStrings) {
        return Arrays.toString(Arrays.stream(nottableStrings).map(Arrays::toString).toArray());
    }

}
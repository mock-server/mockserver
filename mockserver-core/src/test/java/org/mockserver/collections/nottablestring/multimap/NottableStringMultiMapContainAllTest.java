package org.mockserver.collections.nottablestring.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.collections.nottablestring.multimap.NottableStringMultiMapContainAllTest.TestScenario.*;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.schemaParam;

public class NottableStringMultiMapContainAllTest {

    // Test Pattern:
    // EMPTY
    // - empty - DONE
    // IDENTICAL
    // - identical keys and value - DONE
    // - identical keys and multi-values - DONE
    // DIFFERENT CASE
    // - different case keys - DONE
    // - different case values - DONE
    // - different case keys and multi-values - DONE
    // SUBSET
    // - subset values - DONE
    // - subset multi-values - DONE
    // NON MATCHING
    // - non matching keys - DONE
    // - non matching values - DONE
    // - non matching value in multi-value - DONE
    // - non matching keys and values - DONE
    // REGEX
    // - regex keys - DONE
    // - regex values - DONE
    // - regex keys and values - DONE
    // REGEX SUBSET
    // - regex subset values - DONE
    // - regex subset multi-values - DONE
    // REGEX NON MATCHING
    // - non matching regex keys - DONE
    // - non matching regex values - DONE
    // - non matching regex value in multi-value - DONE
    // - non matching regex keys and values - DONE
    // SCHEMA
    // - schema keys - DONE
    // - schema values - DONE
    // - schema keys and values - DONE
    // SCHEMA SUBSET
    // - schema subset values - DONE
    // - schema subset multi-values - DONE
    // SCHEMA NON MATCHING
    // - non matching schema keys - DONE
    // - non matching schema values - DONE
    // - non matching schema keys and values - DONE
    // NOTTED
    // - notted keys - DONE
    // - notted values - DONE
    // - notted value in multi-value - DONE
    // - notted key and value - DONE
    // NOTTED SUBSET
    // - subset notted value in multi-value - DONE
    // - subset notted key and value - DONE
    // NOTTED NOT MATCHING
    // - non matching notted keys - DONE
    // - non matching notted values - DONE
    // - non matching notted value in multi-value - DONE
    // - non matching notted keys and values - DONE
    // OPTIONAL
    // - optional keys - DONE
    // - optional keys with multi-value - TODO
    // OPTIONAL SUBSET
    // - optional key and value
    // - optional keys with multi-value - TODO
    // OPTIONAL NOT MATCHING
    // - non matching optional values - DONE
    // - non matching optional value in multi-value - TODO
    // CONTROL PLANE - REGEX
    // - control plane regex keys - TODO
    // - control plane regex values - TODO
    // - control plane regex keys and values - TODO
    // CONTROL PLANE - REGEX NOT MATCHING
    // - control plane non matching regex keys - TODO
    // - control plane non matching regex values - TODO
    // - control plane non matching regex keys and values - TODO
    // CONTROL PLANE - SCHEMA
    // - control plane schema keys - TODO
    // - control plane schema values - TODO
    // - control plane schema keys and values - TODO
    // CONTROL PLANE - SCHEMA NOT MATCHING
    // - control plane non matching schema keys - TODO
    // - control plane non matching schema values - TODO
    // - control plane non matching schema keys and values - TODO

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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSingleDirection(
            passScenario(new String[]{
                "keyO.*", "valueO.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyO.*", "valueO.*",
                "keyT.*", "valueT.*",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
                "keyTwo", "valueTwo_One",
                "keyTwo", "valueTwo_Two",
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        // can't match regex in reverse (without control plane)
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        // can't match regex in reverse (without control plane)
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSubSetOnly(
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
    }

    // NOTTED SUBSET

    @Test
    public void shouldContainAllSubsetNottedKeysAndValues() {
        // can't match subsets in reverse
        // can't match multiple different values with matching key
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenariosSingleDirectionSubSetMatchingOnly(
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
        shouldPassScenarios(true, true, false, true, testScenarios);
    }

    void shouldPassScenariosSingleDirection(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, false, true, testScenarios);
    }

    void shouldPassScenariosSubSetOnly(TestScenario... testScenarios) {
        shouldPassScenarios(true, true, false, false, testScenarios);
    }

    void shouldPassScenariosSingleDirectionSubSetMatchingOnly(TestScenario... testScenarios) {
        shouldPassScenarios(false, false, false, false, testScenarios);
    }

    void shouldPassScenariosControlPlane(TestScenario[] testScenarios) {
        shouldPassScenarios(false, false, true, true, testScenarios);
    }

    void shouldPassScenarios(boolean bothDirectionsSubSet, boolean bothDirectionsMatchingKey, boolean controlPlane, boolean includeMatchingKey, TestScenario[] testScenarios) {
        for (TestScenario testScenario : testScenarios) {
            // given - sub set
            NottableStringMultiMap matcherForSubSet = multiMap(
                controlPlane,
                KeyMatchStyle.SUB_SET,
                testScenario.matcher
            );
            NottableStringMultiMap matchedForSubSet = multiMap(
                controlPlane,
                KeyMatchStyle.SUB_SET,
                testScenario.matched
            );

            // then
            bidirectionMatch(bothDirectionsSubSet, testScenario, matcherForSubSet, matchedForSubSet, testScenario.result);

            if (includeMatchingKey) {
                // given - matching key
                NottableStringMultiMap matcherForMatchingKey = multiMap(
                    controlPlane,
                    KeyMatchStyle.MATCHING_KEY,
                    testScenario.matcher
                );
                NottableStringMultiMap matchedForMatchingKey = multiMap(
                    controlPlane,
                    KeyMatchStyle.MATCHING_KEY,
                    testScenario.matched
                );

                // then
                bidirectionMatch(bothDirectionsMatchingKey, testScenario, matcherForMatchingKey, matchedForMatchingKey, testScenario.result);
            }
        }
    }

    private void bidirectionMatch(boolean bothDirections, TestScenario testScenario, NottableStringMultiMap matcher, NottableStringMultiMap matched, boolean result) {
        try {
            assertThat(matched.containsAll(matcher), is(result));
        } catch (Throwable throwable) {
            System.out.println("expected " + matcher.getKeyMatchStyle() + " matcher: " + doubleArrayToString(testScenario.matcher) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matched));
            throw throwable;
        }
        if (bothDirections) {
            try {
                assertThat(matcher.containsAll(matched), is(result));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + matcher.getKeyMatchStyle() + " matcher: " + doubleArrayToString(testScenario.matched) + " to " + (result ? "match" : "not match") + " matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        } else if (result) {
            // only do not match in reverse for single directory when matches in non-reverse
            try {
                assertThat(matcher.containsAll(matched), is(false));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + matcher.getKeyMatchStyle() + " matcher: " + doubleArrayToString(testScenario.matched) + " to not match matched: " + doubleArrayToString(testScenario.matcher));
                throw throwable;
            }
        }
    }

    private String doubleArrayToString(NottableString[][] nottableStrings) {
        return Arrays.toString(Arrays.stream(nottableStrings).map(Arrays::toString).toArray());
    }

}
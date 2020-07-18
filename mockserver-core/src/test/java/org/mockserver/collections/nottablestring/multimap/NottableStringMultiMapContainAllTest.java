package org.mockserver.collections.nottablestring.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.collections.nottablestring.multimap.NottableStringMultiMapContainAllTest.TestScenario.failScenario;
import static org.mockserver.collections.nottablestring.multimap.NottableStringMultiMapContainAllTest.TestScenario.passScenario;

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
    // OPTIONAL SUBSET
    // - optional key and value
    // OPTIONAL NOT MATCHING
    // - non matching optional values - DONE
    // CONTROL PLANE - REGEX
    // - control plane regex keys - TODO
    // - control plane regex values - TODO
    // - control plane regex keys and values - TODO
    // CONTROL PLANE - REGEX NOT MATCHING
    // - control plane non matching regex keys - TODO
    // - control plane non matching regex values - TODO
    // - control plane non matching regex keys and values - TODO

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
                "keyOne", "valueOne_Two",
            }, new String[]{
                "keyOne", "valueOne_One",
                "keyOne", "valueOne_Two",
            }),
            passScenario(new String[]{
                "keyOne", "valueOne_ONE",
                "keyONE", "valueOne_Two",
                "keyTwo", "valueTwo_ONE",
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
            })
        );
    }

    public static class TestScenario {

        final String[] matcher;
        final String[] matched;
        final boolean result;

        public static TestScenario passScenario(String[] matcher, String[] matched) {
            return new TestScenario(matcher, matched, true);
        }

        public static TestScenario failScenario(String[] matcher, String[] matched) {
            return new TestScenario(matcher, matched, false);
        }

        public TestScenario(String[] matcher, String[] matched, boolean result) {
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
            System.out.println("expected " + matcher.getKeyMatchStyle() + " matcher: " + Arrays.toString(testScenario.matcher) + " to " + (result ? "match" : "not match") + " matched: " + Arrays.toString(testScenario.matched));
            throw throwable;
        }
        if (bothDirections) {
            try {
                assertThat(matcher.containsAll(matched), is(result));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + matcher.getKeyMatchStyle() + " matcher: " + Arrays.toString(testScenario.matched) + " to " + (result ? "match" : "not match") + " matched: " + Arrays.toString(testScenario.matcher));
                throw throwable;
            }
        } else if (result) {
            // only do not match in reverse for single directory when matches in non-reverse
            try {
                assertThat(matcher.containsAll(matched), is(false));
            } catch (Throwable throwable) {
                System.out.println("expected reverse direction " + matcher.getKeyMatchStyle() + " matcher: " + Arrays.toString(testScenario.matched) + " to not match matched: " + Arrays.toString(testScenario.matcher));
                throw throwable;
            }
        }
    }

}
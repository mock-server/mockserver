package org.mockserver.collections.nottablestring.multimap;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.collections.nottablestring.multimap.NottableStringMultiMapContainAllTest.TestScenario.*;

public class NottableStringMultiMapContainAllTest {

    // Test Pattern
    // - identical keys and value - DONE
    // - identical keys and multi-values - DONE
    // - different case keys - TODO
    // - different case values - TODO
    // - different case keys and multi-values - TODO
    // - regex keys - DONE
    // - regex values - DONE
    // - regex keys and values - DONE
    // - subset values - DONE
    // - subset multi-values - DONE
    // - empty - DONE
    // - non matching keys - DONE
    // - non matching values - DONE
    // - non matching value in multi-value - DONE
    // - non matching keys and values - DONE
    // - non matching regex keys - DONE
    // - non matching regex values - DONE
    // - non matching regex value in multi-value - DONE
    // - non matching regex keys and values - DONE
    // - notted keys
    // - notted values
    // - notted key and value
    // - non matching notted keys
    // - non matching notted values
    // - non matching notted keys and values
    // - optional keys
    // - optional values
    // - optional key and value
    // - non matching optional keys
    // - non matching optional values
    // - non matching optional keys and values
    // - control plane regex keys
    // - control plane regex values
    // - control plane regex keys and values
    // - control plane non matching regex keys
    // - control plane non matching regex values
    // - control plane non matching regex keys and values

    @Test
    public void shouldContainAllIdenticalKeysAndValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalKeysAndMultiValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalRegexKeys() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalRegexValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalRegexKeysAndValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalSubsetValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllIdenticalSubsetMultiValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllEmpty() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingKeys() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingValueInMultiValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingKeysAndValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingRegexKeys() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingRegexValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingRegexValueInMultiValues() {
        shouldPassScenariosSingleDirection(new TestScenario[]{
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
            }),
        });
    }

    @Test
    public void shouldContainAllNotMatchingRegexKeysAndValues() {
        shouldPassScenarios(new TestScenario[]{
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
            }),
        });
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
    void shouldPassScenarios(TestScenario[] testScenarios) {
        shouldPassScenarios(true, false, testScenarios);
    }

    void shouldPassScenariosSingleDirection(TestScenario[] testScenarios) {
        shouldPassScenarios(false, false, testScenarios);
    }

    void shouldPassScenariosControlPlane(TestScenario[] testScenarios) {
        shouldPassScenarios(false, true, testScenarios);
    }

    void shouldPassScenarios(boolean bothDirections, boolean controlPlane, TestScenario[] testScenarios) {
        for (TestScenario testScenario : testScenarios) {
            // given
            NottableStringMultiMap matcher = multiMap(
                controlPlane,
                KeyMatchStyle.SUB_SET,
                testScenario.matcher
            );
            NottableStringMultiMap matched = multiMap(
                controlPlane,
                KeyMatchStyle.SUB_SET,
                testScenario.matched
            );

            // then
            try {
                assertThat(matched.containsAll(matcher), is(testScenario.result));
            } catch (Throwable throwable) {
                System.err.println("expected matcher: " + Arrays.toString(testScenario.matcher) + " to " + (testScenario.result ? "match" : "not match") + " matched: " + Arrays.toString(testScenario.matched));
                throw throwable;
            }
            if (bothDirections) {
                try {
                    assertThat(matcher.containsAll(matched), is(testScenario.result));
                } catch (Throwable throwable) {
                    System.err.println("expected reverse direction matcher: " + Arrays.toString(testScenario.matcher) + " to " + (testScenario.result ? "match" : "not match") + " matched: " + Arrays.toString(testScenario.matched));
                    throw throwable;
                }
            } else if (testScenario.result) {
                // only do not match in reverse for single directory when matches in non-reverse
                try {
                    assertThat(matcher.containsAll(matched), is(false));
                } catch (Throwable throwable) {
                    System.err.println("expected reverse direction matcher: " + Arrays.toString(testScenario.matcher) + " to not match matched: " + Arrays.toString(testScenario.matched));
                    throw throwable;
                }
            }
        }
    }

}
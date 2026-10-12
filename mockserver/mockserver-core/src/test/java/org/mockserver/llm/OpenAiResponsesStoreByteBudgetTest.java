package org.mockserver.llm;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * The byte budget of {@link OpenAiResponsesStore}. Each test builds its own store, so none touches
 * the process-wide instance.
 */
public class OpenAiResponsesStoreByteBudgetTest {

    private static final String TOOLS_ECHO = repeat('t', 50_000);

    private static String repeat(char c, int times) {
        char[] chars = new char[times];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static OpenAiResponsesStore.StoredResponse response(String id, String body) {
        return new OpenAiResponsesStore.StoredResponse(id, ParsedConversation.of(new ArrayList<>(Arrays.asList(
            new ParsedMessage(ParsedMessage.Role.USER, "hello", null, null),
            new ParsedMessage(ParsedMessage.Role.ASSISTANT, "hi", null, null)))), body);
    }

    private static String body(String id) {
        return "{\"id\":\"" + id + "\",\"tools\":\"" + TOOLS_ECHO + "\"}";
    }

    private static long sizeOf(String id) {
        return OpenAiResponsesStore.estimatedSize(response(id, body(id)), null);
    }

    @Test
    public void weighsTheEchoedBodyAndTheConversation() {
        long size = sizeOf("resp_1");

        assertThat(size, greaterThan((long) body("resp_1").length()));
        assertThat(size, lessThanOrEqualTo(body("resp_1").length() + 1_000L));
    }

    @Test
    public void evictsLeastRecentlyUsedOnceOverTheByteBudget() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        store.setMaxSizeInBytes(2 * sizeOf("resp_1") + 100);

        store.put(response("resp_1", body("resp_1")));
        store.put(response("resp_2", body("resp_2")));
        store.put(response("resp_3", body("resp_3")));

        assertThat(store.get("resp_1").isPresent(), is(false));
        assertThat(store.get("resp_2").isPresent(), is(true));
        assertThat(store.get("resp_3").isPresent(), is(true));
        assertThat(store.getTotalBytes(), lessThanOrEqualTo(store.getMaxSizeInBytes()));
    }

    @Test
    public void readingAResponseMakesItMostRecentlyUsed() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        store.setMaxSizeInBytes(2 * sizeOf("resp_1") + 100);

        store.put(response("resp_1", body("resp_1")));
        store.put(response("resp_2", body("resp_2")));
        store.get("resp_1");
        store.put(response("resp_3", body("resp_3")));

        assertThat(store.get("resp_1").isPresent(), is(true));
        assertThat(store.get("resp_2").isPresent(), is(false));
    }

    @Test
    public void keepsTheMostRecentResponseEvenWhenItAloneExceedsTheBudget() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        store.setMaxSizeInBytes(1_000);

        store.put(response("resp_1", body("resp_1")));
        store.put(response("resp_2", body("resp_2")));

        assertThat(store.size(), is(1));
        assertThat(store.get("resp_2").isPresent(), is(true));
        assertThat(store.getTotalBytes(), is(sizeOf("resp_2")));
    }

    @Test
    public void loweringTheBudgetEvictsAtOnce() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        for (int i = 0; i < 5; i++) {
            store.put(response("resp_" + i, body("resp_" + i)));
        }
        assertThat(store.size(), is(5));

        store.setMaxSizeInBytes(2 * sizeOf("resp_1") + 100);

        assertThat(store.size(), is(2));
        assertThat(store.get("resp_3").isPresent(), is(true));
        assertThat(store.get("resp_4").isPresent(), is(true));
    }

    @Test
    public void zeroBudgetBoundsByCountOnly() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(3);
        store.setMaxSizeInBytes(0);

        for (int i = 0; i < 5; i++) {
            store.put(response("resp_" + i, body("resp_" + i)));
        }

        assertThat(store.size(), is(3));
        assertThat(store.get("resp_1").isPresent(), is(false));
        assertThat(store.get("resp_2").isPresent(), is(true));
        assertThat(store.getTotalBytes(), is(3 * sizeOf("resp_1")));
    }

    @Test
    public void replacingAResponseRechargesIt() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);

        store.put(response("resp_1", body("resp_1")));
        store.put(response("resp_1", "{\"id\":\"resp_1\"}"));

        assertThat(store.size(), is(1));
        assertThat(store.getTotalBytes(), is(OpenAiResponsesStore.estimatedSize(response("resp_1", "{\"id\":\"resp_1\"}"), null)));
    }

    @Test
    public void resetReleasesTheChargedBytes() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        store.put(response("resp_1", body("resp_1")));

        store.reset();

        assertThat(store.size(), is(0));
        assertThat(store.getTotalBytes(), is(0L));
    }

    @Test
    public void chainedTurnIsChargedOnlyForTheMessagesItAdds() {
        OpenAiResponsesStore store = new OpenAiResponsesStore(10_000);
        String longToolOutput = repeat('o', 20_000);
        List<ParsedMessage> firstTurn = new ArrayList<>(Arrays.asList(
            new ParsedMessage(ParsedMessage.Role.USER, longToolOutput, null, null)));
        store.recordIfStored("{\"input\":\"first\"}", ParsedConversation.of(firstTurn), null, "{\"id\":\"resp_1\"}");
        long afterFirst = store.getTotalBytes();
        assertThat(afterFirst, greaterThan(20_000L));

        // the codec's decode of a chained turn reuses the stored turn's message objects
        List<ParsedMessage> chained = new ArrayList<>(store.get("resp_1").get().getConversation().getMessages());
        chained.add(new ParsedMessage(ParsedMessage.Role.USER, "next", null, null));
        store.recordIfStored("{\"input\":\"next\",\"previous_response_id\":\"resp_1\"}",
            ParsedConversation.of(chained), null, "{\"id\":\"resp_2\"}");

        long chargedForSecond = store.getTotalBytes() - afterFirst;
        assertThat(chargedForSecond, lessThanOrEqualTo(2_000L));
    }
}

package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Session;
import org.mockserver.scheduler.Scheduler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.API;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherSequentialResponsesTest {

    private MockServerMatcher mockServerMatcher;
    private Session mockServerSession;

    @Before
    public void prepareTestFixture() {
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerSession = new Session();
        mockServerMatcher = new MockServerMatcher(mockLogFormatter, scheduler, webSocketClientRegistry, mockServerSession);
    }

    @Test
    public void respondWhenPathMatchesExpectationWithLimitedMatchesWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        mockServerMatcher.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody2"));
        mockServerMatcher.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(response().withBody("somebody3"));
        mockServerMatcher.add(expectationTwo, API);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationTwo, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithPriorityAndLimitedMatchesWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        mockServerMatcher.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited(), 10).thenRespond(response().withBody("somebody2"));
        mockServerMatcher.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(response().withBody("somebody3"));
        mockServerMatcher.add(expectationTwo, API);

        // then
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationTwo, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithPriorityWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        mockServerMatcher.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 10).thenRespond(response().withBody("somebody2"));
        mockServerMatcher.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 5).thenRespond(response().withBody("somebody3"));
        mockServerMatcher.add(expectationTwo, API);

        // then - match in priority order 10 (one) -> 5 (two) -> 0 (zero)
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));

        // when
        Expectation expectationZeroWithHigherPriority = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 15)
            .withId(expectationZero.getId())
            .thenRespond(response().withBody("somebody1"));
        mockServerMatcher.update(new Expectation[]{expectationZeroWithHigherPriority}, API);

        // then - match in priority order 15 (zero) -> 10 (one) -> 5 (two)
        assertEquals(expectationZeroWithHigherPriority, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));

        // when
        Expectation expectationTwoWithHigherPriority = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 20)
            .withId(expectationTwo.getId())
            .thenRespond(response().withBody("somebody3"));
        mockServerMatcher.update(new Expectation[]{expectationTwoWithHigherPriority}, API);

        // then - match in priority order 20 (two) -> 15 (zero) -> 10 (one)
        assertEquals(expectationTwoWithHigherPriority, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesMultipleDifferentResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath1")).thenRespond(response().withBody("somebody1"));
        mockServerMatcher.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath2")).thenRespond(response().withBody("somebody2"));
        mockServerMatcher.add(expectationOne, API);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
    }

    @Test
    public void doesNotRespondAfterMatchesFinishedExpectedTimes() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody"));
        mockServerMatcher.add(expectationZero, API);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void doesNotRespondAfterTimeToLiveFinishedExpectedTimes() throws InterruptedException {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.exactly(SECONDS, 2L), 0).thenRespond(response().withBody("somebody"));
        mockServerMatcher.add(expectationZero, API);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        MILLISECONDS.sleep(2250L);
        assertEquals(isNull(), mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void responedAfterSessionEntryAddedByPreviousResponse() {
     // when
        Expectation expectation1 = new Expectation(
            request()
            .withSession(new Session().withEntry("key1", "value1"))
        ).thenRespond(
            response()
            .withBody("someBody")
        );
        
        Expectation expectation2 = new Expectation(
            request()
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key1", "value1"))
            .withBody("someBody")
        );
        
        mockServerMatcher.add(expectation1, API);
        mockServerMatcher.add(expectation2, API);
                
        //prior to match - session should remain the same
        assertTrue(mockServerSession.getMap().isEmpty());
        
     //then 
        //expectation2 should be matched because expectation1 requires session entries to exist
        assertEquals(expectation2, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should have the entry that was added by the response of expectation2
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        
        //expectation1 should now be matched because session contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session should still have the entry that was added by the response of expectation2
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        
        //expectation1 should be matched again because session still contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void doesNotResponedAfterSessionClearedByPreviousResponse() {
     // when
        Expectation expectation = new Expectation(
            request()
            .withSession(new Session().withEntry("key1", "value1"))
        ).thenRespond(
            response()
            .withSession(new Session())
            .withBody("someBody")
        );
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
     //then 
        //expectation should be matched because session contains the required entries
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //matched response had explicit empty session - session now should be empty
        assertTrue(mockServerSession.getMap().isEmpty());
        
        //expectation now should not be matched because session is missing the required entries
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session should still be empty
        assertTrue(mockServerSession.getMap().isEmpty());
        
        //expectation should still not be matched because session is missing the required entries
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void responedAfterSessionEntryChangedByPreviousResponse() {
     // when
        Expectation expectation1 = new Expectation(
            request()
            .withSession(new Session().withEntry("key", "value1"))
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key", "value2"))
            .withBody("someBody")
        );
        
        Expectation expectation2 = new Expectation(
            request()
            .withSession(new Session().withEntry("key", "value2"))
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key", "value1"))
            .withBody("someBody")
        );
        
        Expectation expectation3 = new Expectation(
            request()
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key", "value1"))
            .withBody("someBody")
        );
        
        mockServerMatcher.add(expectation1, API);
        mockServerMatcher.add(expectation2, API);
        mockServerMatcher.add(expectation3, API);
                
        //prior to match - session should remain the same
        assertTrue(mockServerSession.getMap().isEmpty());
        
     //then 
        //expectation3 should be matched because expectation1 and expectation2 require session entries to exist
        assertEquals(expectation3, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should have the entry that was added by the response of expectation3
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key")));
        
        //expectation1 should now be matched because session contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should have the entry that was added by the response of expectation1
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value2"), mockServerSession.getMap().get(NottableString.string("key")));
        
        //expectation2 should now be matched because session contains the required entries
        assertEquals(expectation2, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should have the entry value that was changed by the response of expectation2
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key")));
        
        //expectation1 should now be matched because session contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void responedAfterSessionEntriesAddedByPreviousResponses() {
     // when
        Expectation expectation1 = new Expectation(
            request()
            .withSession(new Session()
                .withEntry("key1", "value1")
                .withEntry("key2", "value2")
            )
        ).thenRespond(
            response()
            .withBody("someBody")
        );
        
        Expectation expectation2 = new Expectation(
            request()
            .withSession(new Session().withEntry("key1", "value1"))
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key2", "value2"))
            .withBody("someBody")
        );
        
        Expectation expectation3 = new Expectation(
            request()
        ).thenRespond(
            response()
            .withSession(new Session().withEntry("key1", "value1"))
            .withBody("someBody")
        );
        
        mockServerMatcher.add(expectation1, API);
        mockServerMatcher.add(expectation2, API);
        mockServerMatcher.add(expectation3, API);
                
        //prior to match - session should remain the same
        assertTrue(mockServerSession.getMap().isEmpty());
        
     //then 
        //expectation3 should be matched because expectation1 and expectation2 require session entries to exist
        assertEquals(expectation3, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should have the entry that was added by the response of expectation3
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        
        //expectation2 should now be matched because session contains the required entry for it
        assertEquals(expectation2, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session now should also have the entry that was added by the response of expectation2
        assertEquals(2, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        assertEquals(NottableString.string("value2"), mockServerSession.getMap().get(NottableString.string("key2")));
        
        //expectation1 should now be matched because session contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        
        //session should still have the entries that was added by the response of expectation3 and expectation2 
        assertEquals(2, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        assertEquals(NottableString.string("value2"), mockServerSession.getMap().get(NottableString.string("key2")));
        
        //expectation1 should still be matched because session contains the required entries
        assertEquals(expectation1, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));

    }

}

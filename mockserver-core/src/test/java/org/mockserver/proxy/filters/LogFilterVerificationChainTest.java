package org.mockserver.proxy.filters;

import org.junit.Test;
import org.mockserver.filters.LogFilter;
import org.mockserver.verify.VerificationChain;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogFilterVerificationChainTest {

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify((VerificationChain) null), is(""));
    }

    @Test
    public void shouldPassVerificationChainWithNoRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(

                                )
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationChainWithOneRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("three")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("four")
                                )
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationChainWithTwoRequests() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("three")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("three"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("four")
                                )
                ),
                is(""));
        // then - not next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("three")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("four")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("three"),
                                        request("four")
                                )
                ),
                is(""));
    }

    @Test
    public void shouldFailVerificationChainWithOneRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationChainWithTwoRequestsWrongOrder() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        // then - not next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("four"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("four"),
                                        request("three")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationChainWithTwoRequestsFirstIncorrect() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("zero"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("zero"),
                                        request("three")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("zero"),
                                        request("four")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationChainWithTwoRequestsSecondIncorrect() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("three"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationChainWithThreeRequestsWrongOrder() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("one"),
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        // then - not next to each other
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("four"),
                                        request("one"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));

    }

    @Test
    public void shouldFailVerificationChainWithThreeRequestsDuplicateMissing() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationChain()
                                .withRequests(
                                        request("multi"),
                                        request("multi"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

}

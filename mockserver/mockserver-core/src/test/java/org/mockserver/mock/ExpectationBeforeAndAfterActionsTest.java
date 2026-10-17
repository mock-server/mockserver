package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.model.AfterAction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.AfterAction.afterAction;
import static org.mockserver.model.AfterAction.beforeAction;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The singular {@code withBeforeAction} / {@code withAfterAction} append to the expectation's list, while the
 * plural {@code withBeforeActions} / {@code withAfterActions} replace the whole list.
 */
public class ExpectationBeforeAndAfterActionsTest {

    private static final AfterAction BEFORE_ONE = beforeAction().withHttpRequest(request("/before-one"));
    private static final AfterAction BEFORE_TWO = beforeAction().withHttpRequest(request("/before-two"));
    private static final AfterAction BEFORE_THREE = beforeAction().withHttpRequest(request("/before-three"));
    private static final AfterAction AFTER_ONE = afterAction().withHttpRequest(request("/after-one"));
    private static final AfterAction AFTER_TWO = afterAction().withHttpRequest(request("/after-two"));
    private static final AfterAction AFTER_THREE = afterAction().withHttpRequest(request("/after-three"));

    private static Expectation expectation() {
        return Expectation.when(request("/trigger")).thenRespond(response().withBody("ok"));
    }

    @Test
    public void shouldAppendEachChainedSingularAfterAction() {
        Expectation expectation = expectation()
            .withAfterAction(AFTER_ONE)
            .withAfterAction(AFTER_TWO);

        assertThat(expectation.getAfterActions(), contains(AFTER_ONE, AFTER_TWO));
    }

    @Test
    public void shouldAppendEachChainedSingularBeforeAction() {
        Expectation expectation = expectation()
            .withBeforeAction(BEFORE_ONE)
            .withBeforeAction(BEFORE_TWO);

        assertThat(expectation.getBeforeActions(), contains(BEFORE_ONE, BEFORE_TWO));
    }

    @Test
    public void shouldAppendSingularActionsToAListSetByThePluralForm() {
        Expectation expectation = expectation()
            .withBeforeActions(BEFORE_ONE, BEFORE_TWO)
            .withBeforeAction(BEFORE_THREE)
            .withAfterActions(AFTER_ONE, AFTER_TWO)
            .withAfterAction(AFTER_THREE);

        assertThat(expectation.getBeforeActions(), contains(BEFORE_ONE, BEFORE_TWO, BEFORE_THREE));
        assertThat(expectation.getAfterActions(), contains(AFTER_ONE, AFTER_TWO, AFTER_THREE));
    }

    @Test
    public void shouldReplaceTheWholeListWithThePluralForm() {
        Expectation expectation = expectation()
            .withBeforeAction(BEFORE_ONE)
            .withBeforeActions(BEFORE_TWO, BEFORE_THREE)
            .withAfterAction(AFTER_ONE)
            .withAfterActions(java.util.Arrays.asList(AFTER_TWO, AFTER_THREE));

        assertThat(expectation.getBeforeActions(), contains(BEFORE_TWO, BEFORE_THREE));
        assertThat(expectation.getAfterActions(), contains(AFTER_TWO, AFTER_THREE));
    }

    @Test
    public void shouldIgnoreANullSingularAction() {
        Expectation expectation = expectation()
            .withBeforeAction(null)
            .withAfterAction(null);

        assertThat(expectation.getBeforeActions(), nullValue());
        assertThat(expectation.getAfterActions(), nullValue());
    }

    @Test
    public void shouldEqualTheSameActionsSetByThePluralFormIncludingACachedHashCode() {
        Expectation appended = expectation().withAfterAction(AFTER_ONE);
        int hashCodeBeforeAppend = appended.hashCode();
        appended.withAfterAction(AFTER_TWO).withBeforeAction(BEFORE_ONE);

        Expectation replaced = expectation()
            .withAfterActions(AFTER_ONE, AFTER_TWO)
            .withBeforeActions(BEFORE_ONE);

        assertThat(appended, is(replaced));
        assertThat(appended.hashCode(), is(replaced.hashCode()));
        assertThat(appended.hashCode() == hashCodeBeforeAppend, is(false));
    }

    @Test
    public void shouldNotShareTheActionListWithAClone() {
        Expectation original = expectation().withAfterAction(AFTER_ONE).withBeforeAction(BEFORE_ONE);
        Expectation clone = original.clone();

        clone.withAfterAction(AFTER_TWO).withBeforeAction(BEFORE_TWO);

        assertThat(original.getAfterActions(), contains(AFTER_ONE));
        assertThat(original.getBeforeActions(), contains(BEFORE_ONE));
        assertThat(clone.getAfterActions(), contains(AFTER_ONE, AFTER_TWO));
        assertThat(clone.getBeforeActions(), contains(BEFORE_ONE, BEFORE_TWO));
    }
}

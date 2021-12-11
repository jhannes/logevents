package org.logevents.observers;

import org.junit.Test;
import org.logevents.core.LogEventPredicate;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.core.LogEventPredicate.RequiredMarkerCondition;
import org.logevents.core.LogEventPredicate.SuppressedMarkerCondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.logevents.optional.junit.LogEventSampler.AUDIT;
import static org.logevents.optional.junit.LogEventSampler.HTTP_REQUEST;
import static org.logevents.optional.junit.LogEventSampler.LIFECYCLE;
import static org.logevents.optional.junit.LogEventSampler.OPS;

public class LogEventPredicateTest {

    @Test
    public void shouldCombineTwoSuppressedMarkersIntoOne() {
        LogEventPredicate condition = new SuppressedMarkerCondition("marker!=OPS|HTTP_REQUEST")
                .and(new SuppressedMarkerCondition("marker!=PERFORMANCE"));
        assertEquals("SuppressedMarkerCondition{OPS|PERFORMANCE|HTTP_REQUEST}", condition.toString());
        assertTrue(condition.test());
        assertTrue(condition.test(AUDIT));
        assertFalse(condition.test(LIFECYCLE));
    }

    @Test
    public void shouldReduceRequiredMarkers() {
        assertEquals(
                "Never",
                new RequiredMarkerCondition("marker=OPS|LIFECYCLE").and(new RequiredMarkerCondition("marker=HTTP_REQUEST")).toString()
        );
        assertEquals(
                "Always",
                new RequiredMarkerCondition("marker=OPS|LIFECYCLE").and(new RequiredMarkerCondition("marker=HTTP_REQUEST")).negate().toString()
        );
        assertEquals(
                "RequiredMarkerCondition{HTTP_ASSET_REQUEST [ HTTP_REQUEST ]|LIFECYCLE [ OPS ]}",
                new RequiredMarkerCondition("marker=OPS|HTTP_ASSET_REQUEST").and(new RequiredMarkerCondition("marker=LIFECYCLE|HTTP_REQUEST")).toString()
        );
        assertEquals(
                "RequiredMarkerCondition{OPS}",
                new RequiredMarkerCondition("marker=OPS").and(new SuppressedMarkerCondition("marker=HTTP_REQUEST")).toString()
        );
    }

    @Test
    public void shouldCombineMdcAndMarkerConditions() {
        LogEventPredicate condition = new RequiredMarkerCondition("marker=OPS|PERFORMANCE").and(new LogEventPredicate.RequiredMdcCondition("user", "admin|tester"));
        assertEquals(
                "RequiredMarkerCondition{OPS|PERFORMANCE} AND RequiredMdcCondition{user in [tester, admin]}",
                condition.toString()
        );
        assertTrue(condition.test(new LogEventSampler().withMarker(OPS).withMdc("user", "admin").build()));
        assertFalse(condition.test(new LogEventSampler().withMarker(HTTP_REQUEST).withMdc("user", "admin").build()));
        assertFalse(condition.test(new LogEventSampler().withMarker(OPS).withMdc("user", "other").build()));
    }

    @Test
    public void shouldNegateCombinedMdcAndMarkerCondition() {
        LogEventPredicate condition = new RequiredMarkerCondition("marker=OPS|PERFORMANCE")
                .and(new LogEventPredicate.RequiredMdcCondition("user", "admin|tester"))
                .negate();
        assertEquals(
                "(NOT RequiredMarkerCondition{OPS|PERFORMANCE} AND RequiredMdcCondition{user in [tester, admin]})",
                condition.toString()
        );
        assertFalse(condition.test(new LogEventSampler().withMarker(OPS).withMdc("user", "admin").build()));
        assertTrue(condition.test(new LogEventSampler().withMarker(HTTP_REQUEST).withMdc("user", "admin").build()));
        assertTrue(condition.test(new LogEventSampler().withMarker(OPS).withMdc("user", "other").build()));
    }
}

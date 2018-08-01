package org.logevents.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class CircularBufferTest {

    @Test
    public void shouldInsertInitialElements() {
        CircularBuffer<String> buffer = new CircularBuffer<>();
        buffer.addAll(Arrays.asList("A", "B", "C", "D"));
        assertEquals(Arrays.asList("A", "B", "C", "D"), Arrays.asList(buffer.toArray()));
    }

    @Test
    public void shouldOverflowElementsAfterCapacityReached() {
        CircularBuffer<String> buffer = new CircularBuffer<>(4);
        buffer.addAll(Arrays.asList("A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "E"));
        assertEquals(Arrays.asList("B", "C", "D", "E"), Arrays.asList(buffer.toArray()));
    }

    @Test
    public void shouldOnlyContainElementNotOverflown() {
        CircularBuffer<String> buffer = new CircularBuffer<>(4);
        buffer.addAll(Arrays.asList("A", "B", "C", "D", "E", "F"));
        assertTrue(buffer.containsAll(Arrays.asList("C", "D", "E", "F")));
        assertFalse(buffer.containsAll(Arrays.asList("A", "B", "C")));
    }

}

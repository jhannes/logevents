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
        assertTrue(buffer.isEmpty());
        buffer.addAll(Arrays.asList("A", "B", "C", "D"));
        assertFalse(buffer.isEmpty());
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
        assertEquals(Arrays.asList("C", "D", "E", "F"), Arrays.asList(buffer.toArray()));
        assertTrue(buffer.containsAll(Arrays.asList("C", "D", "E", "F")));
        assertFalse(buffer.containsAll(Arrays.asList("A", "B", "C")));
    }

    @Test
    public void shouldCopyPartialArray() {
        CircularBuffer<String> buffer = new CircularBuffer<>(4);
        buffer.addAll(Arrays.asList("A", "B", "C", "D", "E", "F"));
        String[] array = new String[2];
        buffer.toArray(array);
        assertEquals(Arrays.asList("C", "D"), Arrays.asList(array));
    }

}

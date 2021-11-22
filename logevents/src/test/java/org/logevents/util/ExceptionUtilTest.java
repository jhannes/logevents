package org.logevents.util;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExceptionUtilTest {

    @Test
    public void shouldRethrowExceptionFromFunction() {
        try {
            Stream.of("what://something!com/crzy", "https://www.google.com")
                    .map(ExceptionUtil.softenFunctionExceptions(URL::new))
                    .forEach(System.out::println);
            fail("Should throw on illegal URL");
        } catch (MalformedURLException e) {
            assertEquals("java.net.MalformedURLException: unknown protocol: what", e.toString());
        }
    }

    @Test
    public void shouldRethrowExceptionFromConsumer() {
        try {
            Stream.of("what://something!com/crzy", "https://www.google.com")
                    .forEach(ExceptionUtil.softenExceptions(s -> System.out.println(new URL(s))));
            fail("Should throw on illegal URL");
        } catch (MalformedURLException e) {
            assertEquals("java.net.MalformedURLException: unknown protocol: what", e.toString());
        }
    }
}

package org.logevents.util;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Force checked exceptions to be treated by the compiler as unchecked. Especially useful for functional
 * programming with streams. For example, in order to read all lines from a list of files, you can use:
 * <pre>
 *     List&lt;List&lt;String&gt;&gt; lines = paths.stream()
 *                 .map(ExceptionUtil.softenExceptions(Files::readAllLines))
 *                 .collect(Collectors.toList());
 * </pre>
 * Files::readAllLines throws IOException and without ExceptionUtil, is not easy to use with streams.
 */
public class ExceptionUtil {
    @FunctionalInterface
    public interface FunctionWithCheckedException<T, U, EXCEPTION extends Exception> {
        U apply(T o) throws EXCEPTION;
    }

    @FunctionalInterface
    public interface ConsumerWithCheckedException<T, EXCEPTION extends Exception> {
        void apply(T o) throws EXCEPTION;
    }

    public static <T, U, E extends Exception> Function<T, U> handleException(
            FunctionWithCheckedException<T, U, E> f,
            Function<Exception, U> handler
    ) {
        return o -> {
            try {
                return f.apply(o);
            } catch (Exception e) {
                return handler.apply(e);
            }
        };
    }

    public static <T> Consumer<T> softenExceptions(ConsumerWithCheckedException<T, Exception> f) {
        return o -> {
            try {
                f.apply(o);
            } catch (Exception e) {
                throw softenException(e);
            }
        };
    }

    public static <T, U, EX extends Exception> Function<T, U> softenFunctionExceptions(FunctionWithCheckedException<T, U, EX> f) throws EX {
        return handleException(f, e -> {
            throw softenException(e);
        });
    }

    public static RuntimeException softenException(Exception e) {
        return softenExceptionHelper(e);
    }

    /**
     * Uses template type erasure to trick the compiler into removing checking of exception. The compiler
     * treats E as RuntimeException, meaning that {@link #softenException} doesn't need to declare it,
     * but the runtime treats E as Exception (because of type erasure), which avoids
     * {@link ClassCastException}.
     */
    private static <E extends Exception> E softenExceptionHelper(Exception e) throws E {
        throw (E)e;
    }

}

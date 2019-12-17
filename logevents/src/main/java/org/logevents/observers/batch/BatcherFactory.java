package org.logevents.observers.batch;

import java.util.List;
import java.util.function.Consumer;

public interface BatcherFactory {

    <T> Batcher<T> createBatcher(Consumer<List<T>> processor);

}

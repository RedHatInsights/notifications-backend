package com.redhat.cloud.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class DelayedThrower {

    /**
     * This method should be used around a loop where:
     * <ul>
     * <li>exceptions thrown during loop iterations must NOT interrupt the loop</li>
     * <li>all exceptions must be rethrown at the end of the loop</li>
     * </ul>
     * The exceptions are embedded as suppressed exceptions into a {@link DelayedException}.
     * <br><br>
     * Code example:
     * <pre>
     * public void doSomething() {
     *     DelayedThrower.throwEventually("Something went wrong", accumulator -> {
     *         for (int i = 0; i < 10; i++) {
     *             try {
     *                 // Do something that can throw an exception.
     *             } catch (Exception e) {
     *                 accumulator.add(e);
     *             }
     *         }
     *     });
     * }
     * </pre>
     * @param exceptionMessage the message of the {@link DelayedException} that may be thrown eventually
     * @param consumer the consumer
     * @throws NullPointerException if {@code consumer} is null
     */
    public static void throwEventually(String exceptionMessage, Consumer<List<Exception>> consumer) {
        Objects.requireNonNull(consumer);
        List<Exception> accumulator = new ArrayList<>();
        consumer.accept(accumulator);
        if (!accumulator.isEmpty()) {
            DelayedException e = new DelayedException(exceptionMessage);
            for (Exception exception : accumulator) {
                if (exception instanceof DelayedException && exception.getSuppressed().length > 0) {
                    for (Throwable t : exception.getSuppressed()) {
                        e.addSuppressed(t);
                    }
                } else {
                    e.addSuppressed(exception);
                }
            }
            throw e;
        }
    }
}

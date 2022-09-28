package com.redhat.cloud.notifications;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DelayedThrowerTest {

    @Test
    void test() {
        AtomicInteger outerIterations = new AtomicInteger();
        AtomicInteger innerIterations = new AtomicInteger();

        DelayedException delayedException = assertThrows(DelayedException.class, () -> {
            DelayedThrower.throwEventually("Something went wrong", outerAccumulator -> {
                for (int i = 0; i < 4; i++) {
                    try {
                        outerIterations.incrementAndGet();
                        switch (i) {
                            case 0:
                                throw new IllegalStateException();
                            case 1:
                                throw new IllegalArgumentException();
                            case 2:
                                DelayedThrower.throwEventually("Something went even worse", innerAccumulator -> {
                                    for (int j = 0; j < 2; j++) {
                                        try {
                                            innerIterations.incrementAndGet();
                                            throw new UnsupportedOperationException();
                                        } catch (Exception innerException) {
                                            innerAccumulator.add(innerException);
                                        }
                                    }
                                });
                                break;
                            default:
                                // Do nothing.
                                break;
                        }
                    } catch (Exception outerException) {
                        outerAccumulator.add(outerException);
                    }
                }
            });
        });

        assertEquals(4, outerIterations.get());
        assertEquals(2, innerIterations.get());
        assertEquals(4, delayedException.getSuppressed().length);
        assertEquals(IllegalStateException.class, delayedException.getSuppressed()[0].getClass());
        assertEquals(IllegalArgumentException.class, delayedException.getSuppressed()[1].getClass());
        assertEquals(UnsupportedOperationException.class, delayedException.getSuppressed()[2].getClass());
        assertEquals(UnsupportedOperationException.class, delayedException.getSuppressed()[3].getClass());
    }
}

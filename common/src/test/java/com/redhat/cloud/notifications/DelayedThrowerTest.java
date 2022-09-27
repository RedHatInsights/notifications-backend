package com.redhat.cloud.notifications;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DelayedThrowerTest {

    @Test
    void test() {
        AtomicInteger iterations = new AtomicInteger();

        DelayedException exception = assertThrows(DelayedException.class, () -> {
            DelayedThrower.throwEventually("Something went wrong", accumulator -> {
                for (int i = 0; i < 6; i++) {
                    try {
                        iterations.incrementAndGet();
                        switch (i % 3) {
                            case 0:
                                throw new IllegalStateException();
                            case 1:
                                throw new IllegalArgumentException();
                            default:
                                // Do nothing.
                                break;
                        }
                    } catch (Exception e) {
                        accumulator.add(e);
                    }
                }
            });
        });

        assertEquals(6, iterations.get());
        assertEquals(DelayedException.class, exception.getClass());
        assertEquals(4, exception.getSuppressed().length);
        assertEquals(IllegalStateException.class, exception.getSuppressed()[0].getClass());
        assertEquals(IllegalArgumentException.class, exception.getSuppressed()[1].getClass());
        assertEquals(IllegalStateException.class, exception.getSuppressed()[2].getClass());
        assertEquals(IllegalArgumentException.class, exception.getSuppressed()[3].getClass());
    }
}

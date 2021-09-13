package com.redhat.cloud.notifications;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import java.util.function.Supplier;

public class TestThreadHelper {

    /**
     * This function must be used to execute test code that would block the Vert.x event loop (e.g. RestAssured calls).
     */
    public static Supplier<Uni<? extends Void>> runOnWorkerThread(Runnable runnable) {
        return () -> Uni.createFrom().item(() -> {
            runnable.run();
            return (Void) null;
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}

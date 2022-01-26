package com.redhat.cloud.notifications.processors.webclient;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Use this qualifier to inject a {@link io.vertx.mutiny.ext.web.client.WebClient WebClient} instance to process BOP requests.
 */
@Qualifier
@Retention(RUNTIME)
@Target({METHOD, FIELD})
public @interface BopWebClient {
}

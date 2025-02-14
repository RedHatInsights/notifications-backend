package com.redhat.cloud.notifications.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that is simply used to tell the {@link AuthorizationInterceptor}
 * which parameter contains the received integration identifier.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface IntegrationId {
}

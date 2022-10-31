package com.redhat.cloud.notifications.models.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the annotation to make sure that the provided {@link String} field is a valid {@link java.net.URL}, contains
 * a proper {@link java.net.URI}, that it has a "http" or "https" scheme, and that the host doesn't point to a private
 * IP. For the reasoning behind this last validation please check
 * <a href="https://issues.redhat.com/browse/RHCLOUD-21830">RHCLOUD-21830</a> for more details.
 */
@Constraint(validatedBy = ValidNonPrivateUrlValidator.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ValidNonPrivateUrl {
    Class<?>[] groups() default {};
    String message() default ValidNonPrivateUrlValidator.INVALID_URL;
    Class<? extends Payload>[] payload() default {};
}

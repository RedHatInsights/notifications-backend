package com.redhat.cloud.notifications.routers.dailydigest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class TriggerDailyDigestRequestTest {

    private static Validator validator;

    /**
     * Sets up the validator.
     */
    @BeforeAll
    public static void setUp() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            validator = validatorFactory.getValidator();
        }
    }

    /**
     * Tests that when a {@link TriggerDailyDigestRequest} object which
     * has a blank application name gets validated, a constraint violation is
     * raised.
     */
    @Test
    void testBlankApplicationName() {
        final String[] blankAppNames = {null, "", "     "};

        for (final String blankAppName : blankAppNames) {
            final TriggerDailyDigestRequest blankApplicationName = new TriggerDailyDigestRequest(
                    blankAppName,
                    "bundle-name",
                    UUID.randomUUID(),
                UUID.randomUUID(),
                    "test-blank-application-name-org-id",
                    null,
                    null
            );

            final var constraintViolations = validator.validate(blankApplicationName);
            Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals("applicationName", cv.getPropertyPath().toString(), "an unexpected property raised the constraint violation");
                Assertions.assertEquals("must not be blank", cv.getMessage(), "unexpected error message");
            }
        }
    }

    /**
     * Tests that when a {@link TriggerDailyDigestRequest} object which
     * has a blank bundle name gets validated, a constraint violation is raised.
     */
    @Test
    void testBlankBundleName() {
        final String[] blankBundleNames = {null, "", "     "};

        for (final String blankBundleName : blankBundleNames) {
            final TriggerDailyDigestRequest blankApplicationName = new TriggerDailyDigestRequest(
                    "application-name",
                    blankBundleName,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "test-blank-bundle-name-org-id",
                    null,
                    null
            );

            final var constraintViolations = validator.validate(blankApplicationName);
            Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals("bundleName", cv.getPropertyPath().toString(), "an unexpected property raised the constraint violation");
                Assertions.assertEquals("must not be blank", cv.getMessage(), "unexpected error message");
            }
        }
    }

    /**
     * Tests that when a {@link TriggerDailyDigestRequest} object which
     * has a blank organization id gets validated, a constraint violation is
     * raised.
     */
    @Test
    void testBlankOrgId() {
        final String[] blankOrgIds = {null, "", "     "};

        for (final String blankOrgId : blankOrgIds) {
            final TriggerDailyDigestRequest blankApplicationName = new TriggerDailyDigestRequest(
                    "application-name",
                    "bundle-name",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    blankOrgId,
                    null,
                    null
            );

            final var constraintViolations = validator.validate(blankApplicationName);
            Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals("orgId", cv.getPropertyPath().toString(), "an unexpected property raised the constraint violation");
                Assertions.assertEquals("must not be blank", cv.getMessage(), "unexpected error message");
            }
        }
    }

    @Test
    void testDefaultLocalDateTimeValues() {
        final TriggerDailyDigestRequest blankApplicationName = new TriggerDailyDigestRequest(
                "application-name",
                "bundle-name",
            null,
            null,
                "test-default-local-date-time-values-org-id",
                null,
                null
        );

        // Assert that the start time is set to midnight UTC of today.
        final LocalTime midnight = LocalTime.MIDNIGHT;
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final LocalDateTime expectedStart = LocalDateTime.of(today, midnight);
        Assertions.assertEquals(expectedStart, blankApplicationName.getStart());

        // Assert that the end time is set to UTC "now". Since there might be
        // a slight difference from the created object's timestamp and the one
        // that gets created later, we allow a maximum of 30 seconds of
        // difference to avoid getting false positives on this test.
        final LocalDateTime expectedEnd = LocalDateTime.now(ZoneOffset.UTC);
        final long seconds = ChronoUnit.SECONDS.between(blankApplicationName.getEnd(), expectedEnd);
        Assertions.assertTrue(seconds < 30, "the expected end time was different in more than 30 seconds");
    }
}

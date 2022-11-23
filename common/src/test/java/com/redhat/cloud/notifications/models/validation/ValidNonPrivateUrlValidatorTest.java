package com.redhat.cloud.notifications.models.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

public class ValidNonPrivateUrlValidatorTest {

    public static final String[] internalHosts = {"https://192.168.0.1", "https://172.16.0.1", "https://10.0.0.1", "https://192.168.0.1", "https://172.16.0.1", "https://10.0.0.1"};
    public static final String[] invalidSchemes = {"ftp://redhat.com"};
    public static final String[] malformedUris = {"https://example.com /hello", "https:/\\/example.com"};
    public static final String[] malformedUrls = {"htt:/example.com", "redhat.com", "redhat"};
    public static final String[] validUrls = new String[]{"http://redhat.com", "https://redhat.com"};
    public static final String[] unknownHosts = {"https://non-existing-webpage-test-one-two-three.com", "http://another-non-existing-webpage.com"};

    private static Validator validator;

    /**
     * Sets up the validator.
     */
    @BeforeAll
    public static void setUp() {
        final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

        validator = validatorFactory.getValidator();

        validatorFactory.close();
    }

    /**
     * A simple DTO class that will help with the annotation validations.
     */
    private static class SimpleDto {
        SimpleDto(final String url) {
            this.url = url;
        }

        @ValidNonPrivateUrl
        private final String url;
    }

    /**
     * Tests that a proper URL with an allowed scheme and an external IP doesn't generate any constraint violations.
     */
    @Test
    public void validUrlTest() {
        for (final var url : validUrls) {
            final var validUrl = new SimpleDto(url);

            final var constraintViolations = validator.validate(validUrl);

            Assertions.assertEquals(0, constraintViolations.size(), "unexpected constraint violations:" + constraintViolations);
        }
    }

    /**
     * Tests that the malformed URLs generate constraint violations.
     */
    @Test
    public void malformedUrlsTest() {
        final var expectedNumberConstraintViolations = 1;
        for (final var malformedUrl : malformedUrls) {
            final var invalid = new SimpleDto(malformedUrl);

            final var constraintViolations = validator.validate(invalid);
            Assertions.assertEquals(expectedNumberConstraintViolations, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals(ValidNonPrivateUrlValidator.INVALID_URL, cv.getMessage(), "unexpected error message received");
            }
        }
    }

    /**
     * Tests that the malformed URIs generate constraint violations.
     */
    @Test
    public void malformedUrisTest() {
        final var expectedNumberConstraintViolations = 1;
        for (final var malformedUri : malformedUris) {
            final var invalid = new SimpleDto(malformedUri);

            final var constraintViolations = validator.validate(invalid);
            Assertions.assertEquals(expectedNumberConstraintViolations, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals(ValidNonPrivateUrlValidator.INVALID_URL, cv.getMessage(), "unexpected error message received");
            }
        }
    }

    /**
     * Tests that URLs with disallowed schemes generate constraint violations.
     */
    @Test
    public void invalidSchemesTest() {
        for (final var scheme : invalidSchemes) {
            final var invalidScheme = new SimpleDto(scheme);

            final var constraintViolations = validator.validate(invalidScheme);
            Assertions.assertEquals(1, constraintViolations.size(), "unexpected constraint violations:" + constraintViolations);

            for (final var cv : constraintViolations) {
                Assertions.assertEquals(ValidNonPrivateUrlValidator.INVALID_SCHEME, cv.getMessage(), "unexpected error message received");
            }
        }
    }

    /**
     * Tests that private IP ranges generate constraint violations. This can also be tested by adding a
     * <code>10.0.0.1 fake-local-host.com</code> line on the <code>/etc/hosts</code> file, which simulates a host in the
     * private and internal network. However, since this is hard to do programmatically, because it requires tampering
     * with {@link java.net.InetAddress} or creating a fake DNS server, it has not been done in the test.
     */
    @Test
    public void internalHostsTest() {
        for (final var internalHost : internalHosts) {
            final var internal = new SimpleDto(internalHost);

            final var constraintViolations = validator.validate(internal);
            Assertions.assertEquals(
                    1,
                    constraintViolations.size(),
                    String.format("unexpected constraint violations for host \"%s\": %s", internalHost, constraintViolations)
            );

            for (final var cv : constraintViolations) {
                Assertions.assertEquals(ValidNonPrivateUrlValidator.PRIVATE_IP, cv.getMessage(), "unexpected error message received");
            }
        }
    }

    /**
     * Tests that unknown hosts generate constraint violations.
     */
    @Test
    public void unknownHostTest() {
        final var expectedNumberConstraintViolations = 1;
        for (final var unknownHost : unknownHosts) {
            final var invalid = new SimpleDto(unknownHost);

            final var constraintViolations = validator.validate(invalid);
            Assertions.assertEquals(expectedNumberConstraintViolations, constraintViolations.size(), "unexpected number of constraint violations");

            for (final var cv : constraintViolations) {
                Assertions.assertEquals(ValidNonPrivateUrlValidator.UNKNOWN_HOST, cv.getMessage(), "unexpected error message received");
            }
        }
    }
}

package com.redhat.cloud.notifications.models.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;

public class ValidNonPrivateUrlValidator implements ConstraintValidator<ValidNonPrivateUrl, String> {

    // Error messages.
    public static final String INVALID_SCHEME = "The endpoint URL must start with \"http\" or \"https\"";
    public static final String INVALID_URL = "The endpoint's URL is invalid";
    public static final String PRIVATE_IP = "The host of the endpoint's URL host resolves to a private IP";
    public static final String UNKNOWN_HOST = "The IP address of the endpoint URL's host cannot be determined";

    // Allowed protocol schemes.
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";

    @Override
    public void initialize(final ValidNonPrivateUrl constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    /**
     * <p>Validates the following things on the provided raw URL:
     * <ul>
     *     <li>The raw URL is a well-formed {@link URL}.</li>
     *     <li>The raw URL contains a well-formed {@link URI}.</li>
     *     <li>The raw URL has a valid "http" or "https" scheme.</li>
     *     <li>The raw URL has a hostname which doesn't point to an internal IP.</li>
     * </ul>
     * </p>
     * @param rawUrl the {@link String} URL to validate.
     * @param constraintValidatorContext the constraint validation context.
     * @return true if the {@link String} contains a well-formed URL, a well-formed URI, a "http" or "https" scheme and
     * a hostname which doesn't point to an internal IP.
     */
    @Override
    public boolean isValid(final String rawUrl, ConstraintValidatorContext constraintValidatorContext) {
        final URL url;
        final URI uri;
        try {
            // May throw a MalformedURLException...
            url = new URL(rawUrl);
            // May throw a URISyntaxException...
            uri = url.toURI();
        } catch (final MalformedURLException | URISyntaxException e) {
            this.replaceDefaultMessage(constraintValidatorContext, INVALID_URL);

            return false;
        }

        final String scheme = uri.getScheme();
        if (!SCHEME_HTTP.equals(scheme) && !SCHEME_HTTPS.equals(scheme)) {
            this.replaceDefaultMessage(constraintValidatorContext, INVALID_SCHEME);

            return false;
        }

        // Check that the URL doesn't point to an internal IP.
        final InetAddress address;
        try {
            address = InetAddress.getByName(url.getHost());
        } catch (UnknownHostException e) {
            this.replaceDefaultMessage(constraintValidatorContext, UNKNOWN_HOST);

            return false;
        }

        // If the given host's IP is in the private range, then it's invalid.
        if (address.isSiteLocalAddress()) {
            this.replaceDefaultMessage(constraintValidatorContext, PRIVATE_IP);

            return false;
        }

        return true;
    }

    /**
     * Replaces the default validator's message with the custom provided message.
     * @param context the validation context to replace the default message from.
     * @param message the validation message that wants to be returned instead.
     */
    private void replaceDefaultMessage(ConstraintValidatorContext context, final String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}

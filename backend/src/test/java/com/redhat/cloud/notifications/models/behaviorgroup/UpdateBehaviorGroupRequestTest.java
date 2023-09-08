package com.redhat.cloud.notifications.models.behaviorgroup;

import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

public class UpdateBehaviorGroupRequestTest {

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
     * Tests that a "display name too long" constraint violation is triggered
     * if the display name exceeds the maximum length specified in the class's
     * annotation.
     * @throws NoSuchFieldException if the {@link CreateBehaviorGroupRequest#displayName} field cannot be found.
     */
    @Test
    void testDisplayNameTooLong() throws NoSuchFieldException {
        final Field classField = UpdateBehaviorGroupRequest.class.getDeclaredField("displayName");
        final Size sizeClassAnnotation = classField.getAnnotation(Size.class);

        final var request = new UpdateBehaviorGroupRequest();
        request.displayName = "a".repeat(sizeClassAnnotation.max() + 1);

        final var constraintViolations = validator.validate(request);

        Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of constraint violations received. CVs: " + constraintViolations);

        for (final var cv : constraintViolations) {
            Assertions.assertEquals("displayName", cv.getPropertyPath().toString(), "unexpected field raised the constraint violation");

            final String expectedErrorMessage = String.format("the display name cannot exceed %s characters", sizeClassAnnotation.max());
            Assertions.assertEquals(expectedErrorMessage, cv.getMessage(), "unexpected constraint violation returned");
        }
    }

    /**
     * Tests that when blank display names are provided to an update behavior
     * group request, a constraint violation is raised.
     */
    @Test
    void testDisplayNameBlank() {
        final String[] invalidDisplayNames = {"", "     "};

        for (final var invalidName : invalidDisplayNames) {
            final UpdateBehaviorGroupRequest request = new UpdateBehaviorGroupRequest();
            request.displayName = invalidName;

            final var constraintViolations = validator.validate(request);

            Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of constraint violations received. CVs: " + constraintViolations);

            for (final var cv : constraintViolations) {
                Assertions.assertEquals("displayNameNotNullAndBlank", cv.getPropertyPath().toString(), "unexpected field raised the constraint violation");
                Assertions.assertEquals("the display name cannot be empty", cv.getMessage(), "unexpected constraint violation returned");
            }
        }
    }

    /**
     * Tests that when null display name is provided, simulating an update
     * operation that wants to leave the display name untouched, no constraint
     * violation is raised.
     */
    @Test
    void testDisplayNameNull() {
        final UpdateBehaviorGroupRequest request = new UpdateBehaviorGroupRequest();
        request.displayName = null;

        final var constraintViolations = validator.validate(request);

        Assertions.assertEquals(0, constraintViolations.size(), "unexpected number of constraint violations received. CVs: " + constraintViolations);
    }
}

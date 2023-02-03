package com.redhat.cloud.notifications.models.behaviorgroup;

import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.Size;
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
}

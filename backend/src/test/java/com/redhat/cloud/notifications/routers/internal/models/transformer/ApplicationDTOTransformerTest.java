package com.redhat.cloud.notifications.routers.internal.models.transformer;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.routers.internal.models.dto.ApplicationDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ApplicationDTOTransformerTest {

    /**
     * Tests that the DTO transformer correctly transforms an application and
     * an internal role from the internal model to the DTO.
     * @throws IllegalAccessException if the "created" field of the application
     * object cannot be set via reflection.
     * @throws NoSuchFieldException if the "created" field of the application
     * object cannot be found.
     */
    @Test
    void testToDTO() throws IllegalAccessException, NoSuchFieldException {
        // Create the application to be transformed.
        final Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setName("application-name");
        application.setDisplayName("application-display-name");
        application.setBundleId(UUID.randomUUID());

        // Set the created field via reflection because the "created" field is
        // loaded from the database, and there is no other option of populating
        // it.
        final Field createdField = application
            .getClass()
            .getSuperclass() // CreationUpdateTimestamped
            .getSuperclass() // CreationTimestamped
            .getDeclaredField("created");
        createdField.setAccessible(true);
        createdField.set(application, LocalDateTime.now());

        // Create the associated internal role for the application.
        final InternalRoleAccess internalRoleAccess = new InternalRoleAccess();
        internalRoleAccess.setRole("internal-role-access");

        // Call the function under test.
        final ApplicationDTO result = ApplicationDTOTransformer.toDTO(application, internalRoleAccess);

        Assertions.assertEquals(result.id, application.getId(), "the application's ID property was not properly transformed to the DTO");
        Assertions.assertEquals(result.name, application.getName(), "the application's name property was not properly transformed to the DTO");
        Assertions.assertEquals(result.displayName, application.getDisplayName(), "the application's display name property was not properly transformed to the DTO");
        Assertions.assertEquals(result.bundleId, application.getBundleId(), "the application's bundle ID property was not properly transformed to the DTO");
        Assertions.assertEquals(result.created, application.getCreated().format(DateTimeFormatter.ISO_DATE_TIME), "the application's created property was not properly transformed to the DTO");
        Assertions.assertEquals(result.ownerRole, internalRoleAccess.getRole(), "the internal role was not properly transformed to the DTO");
    }
}

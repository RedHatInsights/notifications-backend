package com.redhat.cloud.notifications.routers.internal.models.transformer;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.routers.internal.models.dto.ApplicationDTO;

import java.time.format.DateTimeFormatter;

public class ApplicationDTOTransformer {
    /**
     * Takes an application and its associated role and transforms them into
     * the payload for the response.
     * @param application the application to be transformed.
     * @param associatedRole the role to be transformed.
     * @return a DTO with the structure of the response.
     */
    public static ApplicationDTO toDTO(final Application application, final InternalRoleAccess associatedRole) {
        final ApplicationDTO applicationDTO = new ApplicationDTO();

        applicationDTO.id = application.getId();
        applicationDTO.name = application.getName();
        applicationDTO.displayName = application.getDisplayName();
        applicationDTO.bundleId = application.getBundleId();
        applicationDTO.created = application.getCreated().format(DateTimeFormatter.ISO_DATE_TIME);

        if (associatedRole != null) {
            applicationDTO.ownerRole = associatedRole.getRole();
        }

        return applicationDTO;
    }
}

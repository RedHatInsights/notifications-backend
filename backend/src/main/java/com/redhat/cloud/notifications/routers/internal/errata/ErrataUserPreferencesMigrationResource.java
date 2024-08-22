package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Path(Constants.API_INTERNAL + "/team-nado")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
public class ErrataUserPreferencesMigrationResource {
    private static final String USERNAME_KEY = "username";
    private static final String ORG_ID_KEY = "org_id";
    private static final String[] CSV_HEADERS = {USERNAME_KEY, ORG_ID_KEY};

    @Inject
    ErrataMigrationRepository errataMigrationRepository;

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/migrate/json")
    @POST
    public void migrateTeamNadoUserPreferencesJSON(@NotNull @RestForm("jsonFile") InputStream jsonFile) throws IOException {
        final byte[] contents = jsonFile.readAllBytes();

        // Extract the subscriptions from the JSON file.
        final String stringContents = new String(contents, StandardCharsets.UTF_8);
        final JsonArray jsonArray = new JsonArray(stringContents);

        final List<ErrataSubscription> errataSubscriptions = new ArrayList<>();
        for (final Object rawJsonObject : jsonArray) {
            final JsonObject teamNadoSubscription = (JsonObject) rawJsonObject;

            errataSubscriptions.add(new ErrataSubscription(teamNadoSubscription.getString(USERNAME_KEY), teamNadoSubscription.getString(ORG_ID_KEY)));
        }

        Log.infof("Read JSON errata subscriptions file and extracted %s subscriptions from it", errataSubscriptions.size());

        try {
            this.errataMigrationRepository.saveErrataSubscriptions(errataSubscriptions);
        } catch (final Exception e) {
            Log.error("Unable to migrate errata subscriptions due to an exception. The insertions were rolled back", e);
        }
    }
}

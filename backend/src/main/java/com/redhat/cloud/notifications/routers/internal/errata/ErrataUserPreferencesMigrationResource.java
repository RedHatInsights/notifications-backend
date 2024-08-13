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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jboss.resteasy.reactive.RestForm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
    @Path("/migrate/csv")
    @POST
    public void migrateTeamNadoUserPreferencesCSV(@RestForm("csvFile") InputStream csvFile) throws IOException {
        final byte[] contents = csvFile.readAllBytes();

        // Set the format for the CSV file.
        final CSVFormat csvFormat = CSVFormat.DEFAULT
            .builder()
            .setHeader(CSV_HEADERS)
            .setRecordSeparator(System.lineSeparator())
            .setSkipHeaderRecord(true)
            .build();

        // Extract the subscriptions from the CSV file.
        final List<ErrataSubscription> errataSubscriptions = new ArrayList<>();
        final Reader csvReader = new InputStreamReader(new ByteArrayInputStream(contents));
        for (final CSVRecord record : csvFormat.parse(csvReader)) {
            final String username = record.get(USERNAME_KEY);
            final String orgId = record.get(ORG_ID_KEY);

            errataSubscriptions.add(new ErrataSubscription(username, orgId));
        }

        Log.infof("Read CSV errata subscriptions file and extracted %s subscriptions from it", errataSubscriptions.size());

        try {
            this.errataMigrationRepository.saveErrataSubscriptions(errataSubscriptions);
        } catch (final Exception e) {
            Log.error("Unable to migrate errata subscriptions due to an exception. The insertions were rolled back", e);
        }
    }

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

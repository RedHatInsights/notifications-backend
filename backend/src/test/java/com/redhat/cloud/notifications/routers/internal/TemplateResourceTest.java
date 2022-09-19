package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.BadRequestException;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.CrudTestHelpers.createAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.createEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.createInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.getAggregationEmailTemplatesByApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.getAllAggregationEmailTemplates;
import static com.redhat.cloud.notifications.CrudTestHelpers.getAllInstantEmailTemplates;
import static com.redhat.cloud.notifications.CrudTestHelpers.getAllTemplates;
import static com.redhat.cloud.notifications.CrudTestHelpers.getInstantEmailTemplatesByEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.updateTemplate;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TemplateResourceTest extends DbIsolatedTest {

    private static final int OK = 200;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @InjectMock
    @RestClient
    TemplateEngineClient templateEngineClient;

    @Test
    void testAllTemplateEndpoints() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        Template template = buildTemplate("template-name", "template-data");

        // Before we start, the DB shouldn't contain any template.
        assertTrue(getAllTemplates(adminIdentity).isEmpty());

        // This creates the template, retrieves it with a separate REST call and then checks the fields values.
        JsonObject jsonTemplate = createTemplate(adminIdentity, template, 200).get();

        // The template should now be available in the "all templates" list.
        JsonArray jsonTemplates = getAllTemplates(adminIdentity);
        assertEquals(1, jsonTemplates.size());
        jsonTemplates.getJsonObject(0).mapTo(Template.class);
        assertEquals(jsonTemplate.getString("id"), jsonTemplates.getJsonObject(0).getString("id"));

        // Let's update the template and check that the new fields values are correctly persisted.
        template.setName("my-new-template");
        template.setDescription("My new template");
        template.setData("new-template-data");
        updateTemplate(adminIdentity, jsonTemplate.getString("id"), template);

        // Now we'll delete the template and check that it no longer exists with the following line.
        deleteTemplate(adminIdentity, jsonTemplate.getString("id"), 200);

        // We already know the template is gone, but let's check one more time.
        assertTrue(getAllTemplates(adminIdentity).isEmpty());
    }

    @Test
    void testAllInstantEmailTemplateEndpoints() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        // First, we need a bundle, an app and an event type in the DB.
        String bundleId = createBundle(adminIdentity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(adminIdentity, bundleId, "app-name", "App", null, OK).get();
        String eventTypeId = createEventType(adminIdentity, appId, "event-type-name", "Event type", "Event type description", OK).get();

        String appId2 = createApp(adminIdentity, bundleId, "app-name-2", "App2", null, OK).get();
        String eventTypeId2 = createEventType(adminIdentity, appId2, "event-type-name-2", "Event type 2", "Event type description", OK).get();

        // We also need templates that will be linked with the instant email template tested below.
        Template subjectTemplate = buildTemplate("subject-template-name", "template-data");
        JsonObject subjectJsonTemplate = createTemplate(adminIdentity, subjectTemplate, 200).get();
        Template bodyTemplate = buildTemplate("body-template-name", "template-data");
        JsonObject bodyJsonTemplate = createTemplate(adminIdentity, bodyTemplate, 200).get();

        // Before we start, the DB shouldn't contain any instant email template.
        assertTrue(getAllInstantEmailTemplates(adminIdentity).isEmpty());

        // First, we'll create, retrieve and check an instant email template that is NOT linked to an event type.
        InstantEmailTemplate emailTemplate = buildInstantEmailTemplate(null, subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplate = createInstantEmailTemplate(adminIdentity, emailTemplate, 200).get();

        // Then, we'll do the same with another instant email template that is linked to an event type.
        InstantEmailTemplate emailTemplateWithEventType = buildInstantEmailTemplate(eventTypeId, subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplateWithEventType = createInstantEmailTemplate(adminIdentity, emailTemplateWithEventType, 200).get();

        // Then, we'll do the same with another instant email template that is linked to event type 2.
        InstantEmailTemplate emailTemplateWithEventType2 = buildInstantEmailTemplate(eventTypeId2, subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplateWithEventType2 = createInstantEmailTemplate(adminIdentity, emailTemplateWithEventType2, 200).get();

        // At this point, the DB should contain 3 instant email template: the one that wasn't linked to the event type and 2 linked to an event type.
        JsonArray jsonEmailTemplates = getAllInstantEmailTemplates(adminIdentity);
        assertEquals(3, jsonEmailTemplates.size());

        // Querying for the specific application (appId2) should yield only 1
        jsonEmailTemplates = getAllInstantEmailTemplates(adminIdentity, appId2);
        assertEquals(1, jsonEmailTemplates.size());
        jsonEmailTemplates.getJsonObject(0).mapTo(InstantEmailTemplate.class);
        assertEquals(jsonEmailTemplateWithEventType2.getString("id"), jsonEmailTemplates.getJsonObject(0).getString("id"));

        // Now, we'll delete the second instant email template and check that it no longer exists with the following line.
        deleteInstantEmailTemplate(adminIdentity, jsonEmailTemplateWithEventType.getString("id"));

        // Now, we'll delete the third instant email template and check that it no longer exists with the following line.
        deleteInstantEmailTemplate(adminIdentity, jsonEmailTemplateWithEventType2.getString("id"));

        // At this point, the DB should contain one instant email template: the one that wasn't linked to the event type.
        jsonEmailTemplates = getAllInstantEmailTemplates(adminIdentity);
        assertEquals(1, jsonEmailTemplates.size());
        jsonEmailTemplates.getJsonObject(0).mapTo(InstantEmailTemplate.class);
        assertEquals(jsonEmailTemplate.getString("id"), jsonEmailTemplates.getJsonObject(0).getString("id"));

        // Retrieving the instant email templates from the event type ID should return an empty list.
        getInstantEmailTemplatesByEventType(adminIdentity, eventTypeId, false);

        // Let's update the instant email template and check that the new fields values are correctly persisted.
        Template newSubjectTemplate = buildTemplate("new-subject-template-name", "template-data");
        JsonObject newSubjectJsonTemplate = createTemplate(adminIdentity, newSubjectTemplate, 200).get();
        Template newBodyTemplate = buildTemplate("new-body-template-name", "template-data");
        JsonObject newBodyJsonTemplate = createTemplate(adminIdentity, newBodyTemplate, 200).get();
        emailTemplate.setEventTypeId(UUID.fromString(eventTypeId));
        emailTemplate.setSubjectTemplateId(UUID.fromString(newSubjectJsonTemplate.getString("id")));
        emailTemplate.setBodyTemplateId(UUID.fromString(newBodyJsonTemplate.getString("id")));
        updateInstantEmailTemplate(adminIdentity, jsonEmailTemplate.getString("id"), emailTemplate);

        // The instant email template is now linked to an event type.
        // It should be returned if we retrieve all instant email templates from the event type ID.
        JsonObject jsonEmailTemplate2 = getInstantEmailTemplatesByEventType(adminIdentity, eventTypeId, true);
        jsonEmailTemplate2.mapTo(InstantEmailTemplate.class);
        assertEquals(jsonEmailTemplate.getString("id"), jsonEmailTemplate2.getString("id"));
    }

    @Test
    void testAllAggregationEmailTemplateEndpoints() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        // First, we need a bundle and an app in the DB.
        String bundleId = createBundle(adminIdentity, "bundle-name", "Bundle", OK).get();
        String appId = createApp(adminIdentity, bundleId, "app-name", "App", null, OK).get();

        // We also need templates that will be linked with the aggregation email template tested below.
        Template subjectTemplate = buildTemplate("subject-template-name", "template-data");
        JsonObject subjectJsonTemplate = createTemplate(adminIdentity, subjectTemplate, 200).get();
        Template bodyTemplate = buildTemplate("body-template-name", "template-data");
        JsonObject bodyJsonTemplate = createTemplate(adminIdentity, bodyTemplate, 200).get();

        // Before we start, the DB shouldn't contain any aggregation email template.
        assertTrue(getAllAggregationEmailTemplates(adminIdentity).isEmpty());

        // First, we'll create, retrieve and check an aggregation email template that is NOT linked to an app.
        AggregationEmailTemplate emailTemplate = buildAggregationEmailTemplate(null, subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplate = createAggregationEmailTemplate(adminIdentity, emailTemplate, 200).get();

        // Then, we'll do the same with another aggregation email template that is linked to an app.
        AggregationEmailTemplate emailTemplateWithApp = buildAggregationEmailTemplate(appId, subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplateWithApp = createAggregationEmailTemplate(adminIdentity, emailTemplateWithApp, 200).get();

        // Now, we'll delete the second aggregation email template and check that it no longer exists with the following line.
        deleteAggregationEmailTemplate(adminIdentity, jsonEmailTemplateWithApp.getString("id"));

        // At this point, the DB should contain one aggregation email template: the one that wasn't linked to the app.
        JsonArray jsonEmailTemplates = getAllAggregationEmailTemplates(adminIdentity);
        assertEquals(1, jsonEmailTemplates.size());
        jsonEmailTemplates.getJsonObject(0).mapTo(AggregationEmailTemplate.class);
        assertEquals(jsonEmailTemplate.getString("id"), jsonEmailTemplates.getJsonObject(0).getString("id"));

        // Retrieving the aggregation email templates from the app ID should return an empty list.
        jsonEmailTemplates = getAggregationEmailTemplatesByApp(adminIdentity, appId);
        assertTrue(jsonEmailTemplates.isEmpty());

        // Let's update the aggregation email template and check that the new fields values are correctly persisted.
        Template newSubjectTemplate = buildTemplate("new-subject-template-name", "template-data");
        JsonObject newSubjectJsonTemplate = createTemplate(adminIdentity, newSubjectTemplate, 200).get();
        Template newBodyTemplate = buildTemplate("new-body-template-name", "template-data");
        JsonObject newBodyJsonTemplate = createTemplate(adminIdentity, newBodyTemplate, 200).get();
        emailTemplate.setApplicationId(UUID.fromString(appId));
        emailTemplate.setSubjectTemplateId(UUID.fromString(newSubjectJsonTemplate.getString("id")));
        emailTemplate.setBodyTemplateId(UUID.fromString(newBodyJsonTemplate.getString("id")));
        updateAggregationEmailTemplate(adminIdentity, jsonEmailTemplate.getString("id"), emailTemplate);

        // The aggregation email template is now linked to an app.
        // It should be returned if we retrieve all aggregation email templates from the app ID.
        jsonEmailTemplates = getAggregationEmailTemplatesByApp(adminIdentity, appId);
        assertEquals(1, jsonEmailTemplates.size());
        jsonEmailTemplates.getJsonObject(0).mapTo(AggregationEmailTemplate.class);
        assertEquals(jsonEmailTemplate.getString("id"), jsonEmailTemplates.getJsonObject(0).getString("id"));
    }

    @Test
    void testTemplateIncludeCheckBeforeDelete() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        // First, let's make sure the DB does not contain any template.
        assertTrue(getAllTemplates(adminIdentity).isEmpty());

        // Then, we'll persist an outer template, which includes another template.
        Template helloTemplate = buildTemplate("hello-template", "Hello, {#include world-template /}");
        JsonObject jsonHelloTemplate = createTemplate(adminIdentity, helloTemplate, 200).get();

        // We also need to persist the included template.
        Template worldTemplate = buildTemplate("world-template", "World!");
        JsonObject jsonWorldTemplate = createTemplate(adminIdentity, worldTemplate, 200).get();

        // At this point, the DB should contain two templates.
        assertEquals(2, getAllTemplates(adminIdentity).size());

        // If we try to delete the included template, it should fail.
        deleteTemplate(adminIdentity, jsonWorldTemplate.getString("id"), 400);

        // The DB still contains two templates.
        assertEquals(2, getAllTemplates(adminIdentity).size());

        // If we delete the outer template before deleting the included template, both REST calls should be successful.
        deleteTemplate(adminIdentity, jsonHelloTemplate.getString("id"), 200);
        deleteTemplate(adminIdentity, jsonWorldTemplate.getString("id"), 200);

        // We deleted everything, the DB should not contain any template.
        assertTrue(getAllTemplates(adminIdentity).isEmpty());
    }

    @Test
    void testUnauthorizedHttpStatus() {
        UUID notUsed = UUID.randomUUID();

        given()
                .basePath(API_INTERNAL)
                .when().post("/templates")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .when().get("/templates")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().get("/templates/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().put("/templates/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().delete("/templates/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .when().post("/templates/email/instant")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .when().get("/templates/email/instant")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("eventTypeId", notUsed)
                .when().get("/templates/email/instant/eventType/{eventTypeId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().get("/templates/email/instant/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().put("/templates/email/instant/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().delete("/templates/email/instant/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .when().post("/templates/email/aggregation")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .when().get("/templates/email/aggregation")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("appId", notUsed)
                .when().get("/templates/email/aggregation/application/{appId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().get("/templates/email/aggregation/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().put("/templates/email/aggregation/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .pathParam("templateId", notUsed)
                .when().delete("/templates/email/aggregation/{templateId}")
                .then().statusCode(401);

        given()
                .basePath(API_INTERNAL)
                .post("/templates/email/render")
                .then().statusCode(401);
    }

    @Test
    void testInvalidEmailTemplateRendering() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        RenderEmailTemplateRequest request = new RenderEmailTemplateRequest();
        request.setPayload("I am invalid!");
        request.setSubjectTemplate(""); // Not important, won't be used.
        request.setBodyTemplate(""); // Not important, won't be used.

        JsonObject exceptionMessage = new JsonObject();
        exceptionMessage.put("message", "Action parsing failed for payload: I am invalid!");
        BadRequestException badRequest = new BadRequestException(exceptionMessage.toString());
        when(templateEngineClient.render(Mockito.any(RenderEmailTemplateRequest.class))).thenThrow(badRequest);

        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(request))
                .when()
                .post("/templates/email/render")
                .then()
                .contentType(JSON)
                .statusCode(400)
                .extract().asString();

        assertEquals("Action parsing failed for payload: I am invalid!", new JsonObject(responseBody).getString("message"));
    }

    private static Template buildTemplate(String name, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription("My template");
        template.setData(data);
        return template;
    }

    private static InstantEmailTemplate buildInstantEmailTemplate(String eventTypeId, String subjectTemplateId, String bodyTemplateId) {
        InstantEmailTemplate emailTemplate = new InstantEmailTemplate();
        if (eventTypeId != null) {
            emailTemplate.setEventTypeId(UUID.fromString(eventTypeId));
        }
        emailTemplate.setSubjectTemplateId(UUID.fromString(subjectTemplateId));
        emailTemplate.setBodyTemplateId(UUID.fromString(bodyTemplateId));
        return emailTemplate;
    }

    private static AggregationEmailTemplate buildAggregationEmailTemplate(String appId, String subjectTemplateId, String bodyTemplateId) {
        AggregationEmailTemplate emailTemplate = new AggregationEmailTemplate();
        emailTemplate.setSubscriptionType(DAILY);
        if (appId != null) {
            emailTemplate.setApplicationId(UUID.fromString(appId));
        }
        emailTemplate.setSubjectTemplateId(UUID.fromString(subjectTemplateId));
        emailTemplate.setBodyTemplateId(UUID.fromString(bodyTemplateId));
        return emailTemplate;
    }
}

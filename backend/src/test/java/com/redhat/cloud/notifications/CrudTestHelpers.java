package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.internal.models.AddAccessRequest;
import com.redhat.cloud.notifications.routers.internal.models.AddApplicationRequest;
import com.redhat.cloud.notifications.routers.internal.models.InternalApplicationUserPermission;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.Family.familyOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class CrudTestHelpers {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    public static Bundle buildBundle(String name, String displayName) {
        Bundle bundle = new Bundle();
        bundle.setName(name);
        bundle.setDisplayName(displayName);
        return bundle;
    }

    public static Optional<String> createBundle(Header identity, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        return createBundle(identity, bundle, expectedStatusCode);
    }

    public static Optional<String> createBundle(Header identity, Bundle bundle, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(bundle))
                .when()
                .post("/internal/bundles")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertNotNull(jsonBundle.getString("id"));
            assertNotNull(jsonBundle.getString("created"));
            assertEquals(bundle.getName(), jsonBundle.getString("name"));
            assertEquals(bundle.getDisplayName(), jsonBundle.getString("display_name"));

            getBundle(identity, jsonBundle.getString("id"), bundle.getName(), bundle.getDisplayName(), OK);

            return Optional.of(jsonBundle.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getBundle(Header identity, String bundleId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .get("/internal/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertEquals(bundleId, jsonBundle.getString("id"));
            assertEquals(expectedName, jsonBundle.getString("name"));
            assertEquals(expectedDisplayName, jsonBundle.getString("display_name"));
        }
    }

    public static void updateBundle(Header identity, String bundleId, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        updateBundle(identity, bundleId, bundle, expectedStatusCode);
    }

    public static void updateBundle(Header identity, String bundleId, Bundle bundle, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("bundleId", bundleId)
                .body(Json.encode(bundle))
                .put("/internal/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            getBundle(identity, bundleId, bundle.getName(), bundle.getDisplayName(), OK);
        }
    }

    public static void deleteBundle(Header identity, String bundleId, boolean expectedResult) {
        deleteBundle(identity, bundleId, expectedResult, OK);
    }

    public static void deleteBundle(Header identity, String bundleId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .when()
                .delete("/internal/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            response.contentType(JSON);
        }

        if (expectedResult != null) {
            Boolean result = response.extract().body().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    public static Application buildApp(String bundleId, String name, String displayName) {
        Application app = new Application();
        if (bundleId != null) {
            app.setBundleId(UUID.fromString(bundleId));
        }
        app.setName(name);
        app.setDisplayName(displayName);
        return app;
    }

    public static Optional<String> createApp(Header identity, String bundleId, String name, String displayName, @Nullable String ownerRole, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        return createApp(identity, app, ownerRole, expectedStatusCode);
    }

    public static Optional<String> createApp(Header identity, @Nullable Application app, @Nullable String ownerRole, int expectedStatusCode) {
        AddApplicationRequest request = null;

        if (app != null) {
            request = new AddApplicationRequest();
            request.bundleId = app.getBundleId();
            request.displayName = app.getDisplayName();
            request.name = app.getName();
            request.ownerRole = ownerRole;
        }

        String responseBody = given()
                .contentType(JSON)
                .header(identity)
                .body(Json.encode(request))
                .when()
                .post("/internal/applications")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertNotNull(jsonApp.getString("id"));
            assertNotNull(jsonApp.getString("created"));
            assertEquals(app.getBundleId().toString(), jsonApp.getString("bundle_id"));
            assertEquals(app.getName(), jsonApp.getString("name"));
            assertEquals(app.getDisplayName(), jsonApp.getString("display_name"));

            getApp(identity, jsonApp.getString("id"), app.getName(), app.getDisplayName(), OK);

            return Optional.of(jsonApp.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getApps(Header identity, String bundleId, int expectedStatusCode, int expectedAppsCount) {
        String responseBody = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .when()
                .get("/internal/bundles/{bundleId}/applications")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonArray jsonApps = new JsonArray(responseBody);
            assertEquals(expectedAppsCount, jsonApps.size());
            if (expectedAppsCount > 0) {
                for (int i = 0; i < expectedAppsCount; i++) {
                    jsonApps.getJsonObject(i).mapTo(Application.class);
                }
            }
        }
    }

    public static void getApp(Header identity, String appId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .pathParam("appId", appId)
                .get("/internal/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertEquals(appId, jsonApp.getString("id"));
            assertEquals(expectedName, jsonApp.getString("name"));
            assertEquals(expectedDisplayName, jsonApp.getString("display_name"));
        }
    }

    public static void updateApp(Header identity, String bundleId, String appId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        updateApp(identity, appId, app, expectedStatusCode);
    }

    public static void updateApp(Header identity, String appId, Application app, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("appId", appId)
                .body(Json.encode(app))
                .put("/internal/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            getApp(identity, appId, app.getName(), app.getDisplayName(), OK);
        }
    }

    public static void deleteApp(Header identity, String appId, boolean expectedResult) {
        deleteApp(identity, appId, expectedResult, OK);
    }

    public static void deleteApp(Header identity, String appId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("appId", appId)
                .when()
                .delete("/internal/applications/{appId}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            response.contentType(JSON);
        }

        if (expectedResult != null) {
            Boolean result = response.extract().body().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    public static EventType buildEventType(String appId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        if (appId != null) {
            eventType.setApplicationId(UUID.fromString(appId));
        }
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        return eventType;
    }

    public static Optional<String> createEventType(Header identity, String appId, String name, String displayName, String description, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description);
        return createEventType(identity, eventType, expectedStatusCode);
    }

    public static Optional<String> createEventType(Header identity, EventType eventType, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(eventType))
                .when()
                .post("/internal/eventTypes")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonEventType = new JsonObject(responseBody);
            jsonEventType.mapTo(EventType.class);
            assertNotNull(jsonEventType.getString("id"));
            assertEquals(eventType.getApplicationId().toString(), jsonEventType.getString("application_id"));
            assertEquals(eventType.getName(), jsonEventType.getString("name"));
            assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
            assertEquals(eventType.getDescription(), jsonEventType.getString("description"));

            return Optional.of(jsonEventType.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getEventTypes(Header identity, String appId, int expectedStatusCode, int expectedEventTypesCount) {
        String responseBody = given()
                .header(identity)
                .pathParam("appId", appId)
                .when()
                .get("/internal/applications/{appId}/eventTypes")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonArray jsonEventTypes = new JsonArray(responseBody);
            assertEquals(expectedEventTypesCount, jsonEventTypes.size());
            if (expectedEventTypesCount > 0) {
                for (int i = 0; i < expectedEventTypesCount; i++) {
                    jsonEventTypes.getJsonObject(0).mapTo(EventType.class);
                }
            }
        }
    }

    public static void updateEventType(Header identity, String appId, String eventTypeId, String name, String displayName, String description, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description);

        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("eventTypeId", eventTypeId)
                .body(Json.encode(eventType))
                .put("/internal/eventTypes/{eventTypeId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? containsString(TEXT.toString()) : any(String.class));

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            String responseBody = given()
                    .header(identity)
                    .pathParam("appId", eventType.getApplicationId())
                    .when()
                    .get("/internal/applications/{appId}/eventTypes")
                    .then()
                    .statusCode(expectedStatusCode)
                    .contentType(JSON)
                    .extract().asString();

            JsonArray jsonEventTypes = new JsonArray(responseBody);
            for (int i = 0; i < jsonEventTypes.size(); i++) {
                JsonObject jsonEventType = jsonEventTypes.getJsonObject(i);
                jsonEventType.mapTo(EventType.class);
                if (jsonEventType.getString("id").equals(eventTypeId)) {
                    assertEquals(eventType.getName(), jsonEventType.getString("name"));
                    assertEquals(eventType.getDisplayName(), jsonEventType.getString("display_name"));
                    assertEquals(eventType.getDescription(), jsonEventType.getString("description"));
                    break;
                }
                if (i == jsonEventTypes.size() - 1) {
                    fail("Event type not found");
                }
            }
        }
    }

    public static void deleteEventType(Header identity, String eventTypeId, boolean expectedResult) {
        deleteEventType(identity, eventTypeId, expectedResult, OK);
    }

    public static void deleteEventType(Header identity, String eventTypeId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete("/internal/eventTypes/{eventTypeId}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            response.contentType(JSON);
        }

        if (expectedResult != null) {
            Boolean result = response.extract().body().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    private static BehaviorGroup buildDefaultBehaviorGroup(String displayName, String bundleId) {
        BehaviorGroup bg = new BehaviorGroup();
        bg.setDisplayName(displayName);
        bg.setBundleId(UUID.fromString(bundleId));
        return bg;
    }

    public static Optional<String> createDefaultBehaviorGroup(Header identity, String displayName, String bundleId, int expected) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        ValidatableResponse response = given()
                .header(identity)
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .post("/behaviorGroups/default")
                .then()
                .statusCode(expected);

        if (familyOf(expected) == SUCCESSFUL) {
            return Optional.of(
                    response.extract()
                    .body()
                    .jsonPath()
                    .getString("id")
            );
        }

        return Optional.empty();
    }

    public static void updateDefaultBehaviorGroup(Header identity, String behaviorGroupId, String displayName, String bundleId, boolean expectedResult, int expectedStatus) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        ValidatableResponse respoonse = given()
                .header(identity)
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(behaviorGroup))
                .pathParam("id", behaviorGroupId)
                .put("/behaviorGroups/default/{id}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            Boolean result = respoonse.extract().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    public static void deleteDefaultBehaviorGroup(Header identity, String behaviorGroupId, boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .basePath(API_INTERNAL)
                .pathParam("id", behaviorGroupId)
                .delete("/behaviorGroups/default/{id}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            Boolean result = response.extract().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    public static List<BehaviorGroup> getDefaultBehaviorGroups(Header identity) {
        List<?> behaviorGroups = given()
                .header(identity)
                .basePath(API_INTERNAL)
                .get("/behaviorGroups/default")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract()
                .body().as(List.class);

        return behaviorGroups.stream().map(rawBg -> {
            Map<String, Object> mbg = (Map<String, Object>) rawBg;
            BehaviorGroup bg = new BehaviorGroup();
            bg.setId(UUID.fromString(mbg.get("id").toString()));
            bg.setDisplayName(mbg.get("display_name").toString());
            bg.setBundleId(UUID.fromString(mbg.get("bundle_id").toString()));

            return bg;
        }).collect(Collectors.toList());
    }

    public static Optional<List<InternalApplicationUserPermission>> getAccessList(Header identity, int expected) {
        String responseBody = given()
                .header(identity)
                .when()
                .get("internal/access")
                .then()
                .statusCode(expected)
                .extract().asString();

        if (familyOf(expected) == SUCCESSFUL) {
            JsonArray json = new JsonArray(responseBody);
            List<InternalApplicationUserPermission> accessList = json.stream().map(o -> {
                JsonObject jsonObject = (JsonObject) o;
                assertNotNull(jsonObject.getString("application_id"));
                assertNotNull(jsonObject.getString("application_display_name"));
                assertNotNull(jsonObject.getString("role"));

                return jsonObject.mapTo(InternalApplicationUserPermission.class);
            }).collect(Collectors.toList());
            return Optional.of(accessList);
        }

        return Optional.empty();
    }

    public static Optional<String> createInternalRoleAccess(Header identity, String role, String appId, int expected) {

        AddAccessRequest request = new AddAccessRequest();
        request.applicationId = UUID.fromString(appId);
        request.role = role;

        String responseBody = given()
                .header(identity)
                .when()
                .contentType(JSON)
                .body(request)
                .post("internal/access")
                .then()
                .statusCode(expected)
                .extract().asString();

        if (familyOf(expected) == SUCCESSFUL) {
            JsonObject jsonInternalRoleAccess = new JsonObject(responseBody);
            jsonInternalRoleAccess.mapTo(InternalRoleAccess.class);
            assertNotNull(jsonInternalRoleAccess.getString("id"));
            assertEquals(appId, jsonInternalRoleAccess.getString("application_id"));
            assertEquals(role, jsonInternalRoleAccess.getString("role"));
            assertNull(jsonInternalRoleAccess.getString("internal_role"));
            return Optional.of(jsonInternalRoleAccess.getString("id"));
        }

        return Optional.empty();
    }

    public static void  deleteInternalRoleAccess(Header identity, String internalRoleAccessId, int expected) {
        given()
                .header(identity)
                .pathParam("internalRoleAccessId", internalRoleAccessId)
                .delete("internal/access/{internalRoleAccessId}")
                .then()
                .statusCode(expected);
    }

    public static Optional<JsonObject> createTemplate(Header header, Template template, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(template))
                .when().post("/templates")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonTemplate = new JsonObject(responseBody);
            jsonTemplate.mapTo(Template.class);
            assertNotNull(jsonTemplate.getString("id"));
            assertEquals(template.getName(), jsonTemplate.getString("name"));
            assertEquals(template.getDescription(), jsonTemplate.getString("description"));
            assertEquals(template.getData(), jsonTemplate.getString("data"));

            // Let's check that the template has been correctly persisted.
            getTemplate(header, jsonTemplate.getString("id"), template, 200);

            return Optional.of(jsonTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getTemplate(Header header, String templateId, Template expectedTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonTemplate = new JsonObject(responseBody);
            jsonTemplate.mapTo(Template.class);
            assertEquals(expectedTemplate.getName(), jsonTemplate.getString("name"));
            assertEquals(expectedTemplate.getDescription(), jsonTemplate.getString("description"));
            assertEquals(expectedTemplate.getData(), jsonTemplate.getString("data"));
        }
    }

    public static JsonArray getAllTemplates(Header header) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .when().get("/templates")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        return new JsonArray(responseBody);
    }

    public static void updateTemplate(Header header, String templateId, Template updatedTemplate) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedTemplate))
                .when().put("/templates/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the template fields have been correctly updated.
        getTemplate(header, templateId, updatedTemplate, 200);
    }

    public static void deleteTemplate(Header header, String templateId, int expectedStatusCode) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(TEXT);

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            // Let's check that the template no longer exists.
            getTemplate(header, templateId, /* Not used */ null, 404);
        }
    }

    public static Optional<JsonObject> createInstantEmailTemplate(Header header, InstantEmailTemplate emailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(emailTemplate))
                .when().post("/templates/email/instant")
                .then()
                .statusCode(200)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonEmailTemplate = new JsonObject(responseBody);
            jsonEmailTemplate.mapTo(InstantEmailTemplate.class);
            assertNotNull(jsonEmailTemplate.getString("id"));

            // The response should NOT contain the full event type.
            assertNull(jsonEmailTemplate.getJsonObject("event_type"));
            if (emailTemplate.getEventTypeId() == null) {
                assertNull(jsonEmailTemplate.getString("event_type_id"));
            } else {
                assertEquals(emailTemplate.getEventTypeId().toString(), jsonEmailTemplate.getString("event_type_id"));
            }

            // The response should NOT contain the full subject template.
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertEquals(emailTemplate.getSubjectTemplateId().toString(), jsonEmailTemplate.getString("subject_template_id"));

            // The response should NOT contain the full body template.
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
            assertEquals(emailTemplate.getBodyTemplateId().toString(), jsonEmailTemplate.getString("body_template_id"));

            // Let's check that the email template has been correctly persisted.
            getInstantEmailTemplate(header, jsonEmailTemplate.getString("id"), emailTemplate, 200);

            return Optional.of(jsonEmailTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getInstantEmailTemplate(Header header, String templateId, InstantEmailTemplate expectedEmailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/email/instant/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonEmailTemplate = new JsonObject(responseBody);
            jsonEmailTemplate.mapTo(InstantEmailTemplate.class);

            // The response should NOT contain the full event type.
            assertNull(jsonEmailTemplate.getJsonObject("event_type"));
            if (expectedEmailTemplate.getEventTypeId() == null) {
                assertNull(jsonEmailTemplate.getString("event_type_id"));
            } else {
                assertEquals(expectedEmailTemplate.getEventTypeId().toString(), jsonEmailTemplate.getString("event_type_id"));
            }

            // The response should contain the full subject template.
            JsonObject subjectTemplate = jsonEmailTemplate.getJsonObject("subject_template");
            subjectTemplate.mapTo(Template.class);
            assertEquals(expectedEmailTemplate.getSubjectTemplateId().toString(), subjectTemplate.getString("id"));

            // The response should contain the full body template.
            JsonObject bodyTemplate = jsonEmailTemplate.getJsonObject("body_template");
            bodyTemplate.mapTo(Template.class);
            assertEquals(expectedEmailTemplate.getBodyTemplateId().toString(), bodyTemplate.getString("id"));
        }
    }

    public static void deleteInstantEmailTemplate(Header header, String templateId) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/email/instant/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the template no longer exists.
        getInstantEmailTemplate(header, templateId, /* Not used */ null, 404);
    }

    public static JsonArray getAllInstantEmailTemplates(Header header) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .when().get("/templates/email/instant")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonArray jsonEmailTemplates = new JsonArray(responseBody);
        for (int i = 0; i < jsonEmailTemplates.size(); i++) {
            JsonObject jsonEmailTemplate = jsonEmailTemplates.getJsonObject(i);
            if (jsonEmailTemplate.getJsonObject("event_type") != null) {
                assertNull(jsonEmailTemplate.getJsonObject("event_type").getJsonObject("application"));
            }
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
        }

        return jsonEmailTemplates;
    }

    public static JsonArray getInstantEmailTemplatesByEventType(Header header, String eventTypeId) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("eventTypeId", eventTypeId)
                .when().get("/templates/email/instant/eventType/{eventTypeId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonArray jsonEmailTemplates = new JsonArray(responseBody);
        for (int i = 0; i < jsonEmailTemplates.size(); i++) {
            JsonObject jsonEmailTemplate = jsonEmailTemplates.getJsonObject(i);
            assertNull(jsonEmailTemplate.getJsonObject("event_type"));
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
        }

        return jsonEmailTemplates;
    }

    public static void updateInstantEmailTemplate(Header header, String templateId, InstantEmailTemplate updatedEmailTemplate) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedEmailTemplate))
                .when().put("/templates/email/instant/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the instant email template fields have been correctly updated.
        getInstantEmailTemplate(header, templateId, updatedEmailTemplate, 200);
    }

    public static Optional<JsonObject> createAggregationEmailTemplate(Header header, AggregationEmailTemplate emailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .body(Json.encode(emailTemplate))
                .when().post("/templates/email/aggregation")
                .then()
                .statusCode(200)
                .contentType(familyOf(expectedStatusCode) == SUCCESSFUL ? is(JSON_UTF8) : any(String.class))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonEmailTemplate = new JsonObject(responseBody);
            jsonEmailTemplate.mapTo(InstantEmailTemplate.class);
            assertNotNull(jsonEmailTemplate.getString("id"));

            // The response should NOT contain the full application.
            assertNull(jsonEmailTemplate.getJsonObject("application"));
            if (emailTemplate.getApplicationId() == null) {
                assertNull(jsonEmailTemplate.getString("application_id"));
            } else {
                assertEquals(emailTemplate.getApplicationId().toString(), jsonEmailTemplate.getString("application_id"));
            }

            // The response should NOT contain the full subject template.
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertEquals(emailTemplate.getSubjectTemplateId().toString(), jsonEmailTemplate.getString("subject_template_id"));

            // The response should NOT contain the full body template.
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
            assertEquals(emailTemplate.getBodyTemplateId().toString(), jsonEmailTemplate.getString("body_template_id"));

            // Let's check that the email template has been correctly persisted.
            getAggregationEmailTemplate(header, jsonEmailTemplate.getString("id"), emailTemplate, 200);

            return Optional.of(jsonEmailTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getAggregationEmailTemplate(Header header, String templateId, AggregationEmailTemplate expectedEmailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonEmailTemplate = new JsonObject(responseBody);
            jsonEmailTemplate.mapTo(InstantEmailTemplate.class);

            // The response should NOT contain the full application.
            assertNull(jsonEmailTemplate.getJsonObject("application"));
            if (expectedEmailTemplate.getApplicationId() == null) {
                assertNull(jsonEmailTemplate.getString("application_id"));
            } else {
                assertEquals(expectedEmailTemplate.getApplicationId().toString(), jsonEmailTemplate.getString("application_id"));
            }

            // The response should contain the full subject template.
            JsonObject subjectTemplate = jsonEmailTemplate.getJsonObject("subject_template");
            subjectTemplate.mapTo(Template.class);
            assertEquals(expectedEmailTemplate.getSubjectTemplateId().toString(), subjectTemplate.getString("id"));

            // The response should contain the full body template.
            JsonObject bodyTemplate = jsonEmailTemplate.getJsonObject("body_template");
            bodyTemplate.mapTo(Template.class);
            assertEquals(expectedEmailTemplate.getBodyTemplateId().toString(), bodyTemplate.getString("id"));
        }
    }

    public static void deleteAggregationEmailTemplate(Header header, String templateId) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the template no longer exists.
        getAggregationEmailTemplate(header, templateId, /* Not used */ null, 404);
    }

    public static JsonArray getAllAggregationEmailTemplates(Header header) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .when().get("/templates/email/aggregation")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonArray jsonEmailTemplates = new JsonArray(responseBody);
        for (int i = 0; i < jsonEmailTemplates.size(); i++) {
            JsonObject jsonEmailTemplate = jsonEmailTemplates.getJsonObject(i);
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
        }

        return jsonEmailTemplates;
    }

    public static JsonArray getAggregationEmailTemplatesByApp(Header header, String appId) {
        String responseBody = given()
                .basePath(API_INTERNAL)
                .header(header)
                .pathParam("appId", appId)
                .when().get("/templates/email/aggregation/application/{appId}")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonArray jsonEmailTemplates = new JsonArray(responseBody);
        for (int i = 0; i < jsonEmailTemplates.size(); i++) {
            JsonObject jsonEmailTemplate = jsonEmailTemplates.getJsonObject(i);
            assertNull(jsonEmailTemplate.getJsonObject("application"));
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));
        }

        return jsonEmailTemplates;
    }

    public static void updateAggregationEmailTemplate(Header header, String templateId, AggregationEmailTemplate updatedEmailTemplate) {
        given()
                .basePath(API_INTERNAL)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedEmailTemplate))
                .when().put("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the aggregation email template fields have been correctly updated.
        getAggregationEmailTemplate(header, templateId, updatedEmailTemplate, 200);
    }
}

package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.BehaviorGroupActionId;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.internal.models.AddAccessRequest;
import com.redhat.cloud.notifications.routers.internal.models.AddApplicationRequest;
import com.redhat.cloud.notifications.routers.internal.models.InternalApplicationUserPermission;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static jakarta.ws.rs.core.Response.Status.Family.familyOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.NOT_FOUND;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class CrudTestHelpers {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";

    public static ContentType contentTypeForStatusCode(int statusCode, ContentType defaultContentType) {
        if (statusCode == NOT_FOUND) {
            return TEXT;
        }

        return defaultContentType;
    }

    public static Bundle buildBundle(String name, String displayName) {
        Bundle bundle = new Bundle();
        bundle.setName(name);
        bundle.setDisplayName(displayName);
        return bundle;
    }

    public static Optional<String> createBundle(String basePath, Header identity, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        return createBundle(basePath, identity, bundle, expectedStatusCode);
    }

    public static Optional<String> createBundle(String basePath, Header identity, Bundle bundle, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(bundle))
                .when()
                .post(basePath + "/bundles")
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

            getBundle(basePath, identity, jsonBundle.getString("id"), bundle.getName(), bundle.getDisplayName(), OK);

            return Optional.of(jsonBundle.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getBundle(String basePath, Header identity, String bundleId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .get(basePath + "/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonBundle = new JsonObject(responseBody);
            jsonBundle.mapTo(Bundle.class);
            assertEquals(bundleId, jsonBundle.getString("id"));
            assertEquals(expectedName, jsonBundle.getString("name"));
            assertEquals(expectedDisplayName, jsonBundle.getString("display_name"));
        }
    }

    public static void updateBundle(String basePath, Header identity, String bundleId, String name, String displayName, int expectedStatusCode) {
        Bundle bundle = buildBundle(name, displayName);
        updateBundle(basePath, identity, bundleId, bundle, expectedStatusCode);
    }

    public static void updateBundle(String basePath, Header identity, String bundleId, Bundle bundle, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("bundleId", bundleId)
                .body(Json.encode(bundle))
                .put(basePath + "/bundles/{bundleId}")
                .then()
                .statusCode(expectedStatusCode);

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            getBundle(basePath, identity, bundleId, bundle.getName(), bundle.getDisplayName(), OK);
        }
    }

    public static void deleteBundle(String basePath, Header identity, String bundleId, boolean expectedResult) {
        deleteBundle(basePath, identity, bundleId, expectedResult, OK);
    }

    public static void deleteBundle(String basePath, Header identity, String bundleId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .when()
                .delete(basePath + "/bundles/{bundleId}")
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

    public static Optional<String> createApp(String basePath, Header identity, String bundleId, String name, String displayName, @Nullable String ownerRole, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        return createApp(basePath, identity, app, ownerRole, expectedStatusCode);
    }

    public static Optional<String> createApp(String basePath, Header identity, @Nullable Application app, @Nullable String ownerRole, int expectedStatusCode) {
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
                .post(basePath + "/applications")
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

            getApp(basePath, identity, jsonApp.getString("id"), app.getName(), app.getDisplayName(), OK);

            return Optional.of(jsonApp.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getApps(String basePath, Header identity, String bundleId, int expectedStatusCode, int expectedAppsCount) {
        String responseBody = given()
                .header(identity)
                .pathParam("bundleId", bundleId)
                .when()
                .get(basePath + "/bundles/{bundleId}/applications")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
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

    public static void getApp(String basePath, Header identity, String appId, String expectedName, String expectedDisplayName, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .pathParam("appId", appId)
                .get(basePath + "/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonApp = new JsonObject(responseBody);
            jsonApp.mapTo(Application.class);
            assertEquals(appId, jsonApp.getString("id"));
            assertEquals(expectedName, jsonApp.getString("name"));
            assertEquals(expectedDisplayName, jsonApp.getString("display_name"));
        }
    }

    public static void updateApp(String basePath, Header identity, String bundleId, String appId, String name, String displayName, int expectedStatusCode) {
        Application app = buildApp(bundleId, name, displayName);
        updateApp(basePath, identity, appId, app, expectedStatusCode);
    }

    public static void updateApp(String basePath, Header identity, String appId, Application app, int expectedStatusCode) {
        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("appId", appId)
                .body(Json.encode(app))
                .put(basePath + "/applications/{appId}")
                .then()
                .statusCode(expectedStatusCode);

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            getApp(basePath, identity, appId, app.getName(), app.getDisplayName(), OK);
        }
    }

    public static void deleteApp(String basePath, Header identity, String appId, boolean expectedResult) {
        deleteApp(basePath, identity, appId, expectedResult, OK);
    }

    public static void deleteApp(String basePath, Header identity, String appId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("appId", appId)
                .when()
                .delete(basePath + "/applications/{appId}")
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

    public static EventType buildEventType(String appId, String name, String displayName, String description, boolean subscribedByDefault, boolean subscriptionLocked) {
        return buildEventType(appId, name, displayName, description, subscribedByDefault, subscriptionLocked, false);
    }

    public static EventType buildEventType(String appId, String name, String displayName, String description, boolean subscribedByDefault, boolean subscriptionLocked, boolean restrictToRecipientsIntegration) {
        EventType eventType = new EventType();
        if (appId != null) {
            eventType.setApplicationId(UUID.fromString(appId));
        }
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        eventType.setSubscribedByDefault(subscribedByDefault);
        eventType.setSubscriptionLocked(subscriptionLocked);
        eventType.setRestrictToRecipientsIntegrations(restrictToRecipientsIntegration);
        eventType.setAvailableSeverities(Set.of(Severity.MODERATE, Severity.CRITICAL, Severity.LOW));
        return eventType;
    }

    public static Optional<String> createEventType(String basePath, Header identity, String appId, String name, String displayName, String description, boolean subscribedByDefault, boolean subscriptionLocked, int expectedStatusCode) {
        return createEventType(basePath, identity, appId, name, displayName, description, subscribedByDefault, subscriptionLocked, false, expectedStatusCode);
    }

    public static Optional<String> createEventType(String basePath, Header identity, String appId, String name, String displayName, String description, boolean subscribedByDefault, boolean subscriptionLocked, boolean restrictToRecipientsIntegration, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description, subscribedByDefault, subscriptionLocked);
        return createEventType(basePath, identity, eventType, expectedStatusCode);
    }

    public static Optional<String> createEventType(String basePath, Header identity, EventType eventType, int expectedStatusCode) {
        String responseBody = given()
                .header(identity)
                .contentType(JSON)
                .body(Json.encode(eventType))
                .when()
                .post(basePath + "/eventTypes")
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
            assertEquals(eventType.isSubscribedByDefault(), jsonEventType.getBoolean("subscribed_by_default"));
            assertEquals(eventType.isSubscriptionLocked(), jsonEventType.getBoolean("subscription_locked"));
            assertEquals(eventType.isVisible(), jsonEventType.getBoolean("visible"));
            assertEquals(eventType.isIncludedInDrawer(), jsonEventType.getBoolean("included_in_drawer"));

            return Optional.of(jsonEventType.getString("id"));
        } else {
            return Optional.empty();
        }
    }

    public static void getEventTypes(String basePath, Header identity, String appId, int expectedStatusCode, int expectedEventTypesCount) {
        String responseBody = given()
                .header(identity)
                .pathParam("appId", appId)
                .when()
                .get(basePath + "/applications/{appId}/eventTypes")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
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

    public static void updateEventType(String basePath, Header identity, String appId, String eventTypeId, String name, String displayName, String description, boolean subscribedByDefault, boolean subscriptionLocked, boolean restrictToRecipientsIntegration, int expectedStatusCode) {
        EventType eventType = buildEventType(appId, name, displayName, description, subscribedByDefault, subscriptionLocked, restrictToRecipientsIntegration);

        given()
                .contentType(JSON)
                .header(identity)
                .pathParam("eventTypeId", eventTypeId)
                .body(Json.encode(eventType))
                .put(basePath + "/eventTypes/{eventTypeId}")
                .then()
                .statusCode(expectedStatusCode);

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            String responseBody = given()
                    .header(identity)
                    .pathParam("appId", eventType.getApplicationId())
                    .when()
                    .get(basePath + "/applications/{appId}/eventTypes")
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
                    assertEquals(eventType.isSubscribedByDefault(), jsonEventType.getBoolean("subscribed_by_default"));
                    assertEquals(eventType.isSubscriptionLocked(), jsonEventType.getBoolean("subscription_locked"));
                    assertEquals(eventType.isVisible(), jsonEventType.getBoolean("visible"));
                    assertEquals(eventType.isIncludedInDrawer(), jsonEventType.getBoolean("included_in_drawer"));
                    break;
                }
                if (i == jsonEventTypes.size() - 1) {
                    fail("Event type not found");
                }
            }
        }
    }

    public static void updateEventTypeVisibility(String basePath, Header identity, String eventTypeId, boolean isVisible, int expectedStatusCode) {
        given()
            .header(identity)
            .contentType(JSON)
            .pathParam("eventTypeId", eventTypeId)
            .body(String.valueOf(isVisible))
            .put(basePath + "/eventTypes/{eventTypeId}/updateVisibility")
            .then()
            .statusCode(expectedStatusCode);
    }

    public static void deleteEventType(String basePath, Header identity, String eventTypeId, boolean expectedResult) {
        deleteEventType(basePath, identity, eventTypeId, expectedResult, OK);
    }

    public static void deleteEventType(String basePath, Header identity, String eventTypeId, Boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .pathParam("eventTypeId", eventTypeId)
                .when()
                .delete(basePath + "/eventTypes/{eventTypeId}")
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

    public static Optional<String> createDefaultBehaviorGroup(String basePath, Header identity, String displayName, String bundleId, int expected) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        ValidatableResponse response = given()
                .header(identity)
                .basePath(basePath)
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

    public static void updateDefaultBehaviorGroup(String basePath, Header identity, String behaviorGroupId, String displayName, String bundleId, boolean expectedResult, int expectedStatus) {
        BehaviorGroup behaviorGroup = buildDefaultBehaviorGroup(displayName, bundleId);

        ValidatableResponse respoonse = given()
                .header(identity)
                .basePath(basePath)
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

    public static void deleteDefaultBehaviorGroup(String basePath, Header identity, String behaviorGroupId, boolean expectedResult, int expectedStatus) {
        ValidatableResponse response = given()
                .header(identity)
                .basePath(basePath)
                .pathParam("id", behaviorGroupId)
                .delete("/behaviorGroups/default/{id}")
                .then()
                .statusCode(expectedStatus);

        if (familyOf(expectedStatus) == SUCCESSFUL) {
            Boolean result = response.extract().as(Boolean.class);
            assertEquals(expectedResult, result);
        }
    }

    public static List<BehaviorGroup> getDefaultBehaviorGroups(String basePath, Header identity) {
        List<?> behaviorGroups = given()
                .header(identity)
                .basePath(basePath)
                .get("/behaviorGroups/default")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract()
                .body().as(List.class);

        final ObjectMapper mapper = new ObjectMapper();

        return behaviorGroups.stream().map(rawBg -> {
            Map<String, Object> mbg = (Map<String, Object>) rawBg;
            BehaviorGroup bg = new BehaviorGroup();
            bg.setActions(new ArrayList<>());
            bg.setBehaviors(new HashSet<>());
            bg.setId(UUID.fromString(mbg.get("id").toString()));
            bg.setDisplayName(mbg.get("display_name").toString());
            bg.setBundleId(UUID.fromString(mbg.get("bundle_id").toString()));

            List<Object> actions =  (List<Object>) mbg.get("actions");
            for (Object action : actions) {
                Map<String, Object> actionMap = (Map<String, Object>) action;
                actionMap.get("id");
                BehaviorGroupActionId bgaId = mapper.convertValue(actionMap.get("id"), BehaviorGroupActionId.class);
                BehaviorGroupAction bgaAction = new BehaviorGroupAction();
                bgaAction.setId(bgaId);
                bg.getActions().add(bgaAction);
            }

            List<Object> behaviors =  (List<Object>) mbg.get("behaviors");
            for (Object behavior : behaviors) {
                bg.getBehaviors().add(mapper.convertValue(behavior, EventTypeBehavior.class));
            }

            return bg;
        }).collect(Collectors.toList());
    }

    public static Optional<List<InternalApplicationUserPermission>> getAccessList(String basePath, Header identity, int expected) {
        String responseBody = given()
                .header(identity)
                .when()
                .get(basePath + "/access")
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

    public static Optional<String> createInternalRoleAccess(String basePath, Header identity, String role, String appId, int expected) {

        AddAccessRequest request = new AddAccessRequest();
        request.applicationId = UUID.fromString(appId);
        request.role = role;

        String responseBody = given()
                .header(identity)
                .when()
                .contentType(JSON)
                .body(request)
                .post(basePath + "/access")
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

    public static void  deleteInternalRoleAccess(String basePath, Header identity, String internalRoleAccessId, int expected) {
        given()
                .header(identity)
                .pathParam("internalRoleAccessId", internalRoleAccessId)
                .delete(basePath + "/access/{internalRoleAccessId}")
                .then()
                .statusCode(expected);
    }

    public static Optional<JsonObject> createTemplate(String basePath, Header header, Template template, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
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
            getTemplate(basePath, header, jsonTemplate.getString("id"), template, 200);

            return Optional.of(jsonTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getTemplate(String basePath, Header header, String templateId, Template expectedTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
                .extract().asString();

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            JsonObject jsonTemplate = new JsonObject(responseBody);
            jsonTemplate.mapTo(Template.class);
            assertEquals(expectedTemplate.getName(), jsonTemplate.getString("name"));
            assertEquals(expectedTemplate.getDescription(), jsonTemplate.getString("description"));
            assertEquals(expectedTemplate.getData(), jsonTemplate.getString("data"));
        }
    }

    public static JsonArray getAllTemplates(String basePath, Header header) {
        String responseBody = given()
                .basePath(basePath)
                .header(header)
                .when().get("/templates")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        return new JsonArray(responseBody);
    }

    public static void updateTemplate(String basePath, Header header, String templateId, Template updatedTemplate) {
        given()
                .basePath(basePath)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedTemplate))
                .when().put("/templates/{templateId}")
                .then()
                .statusCode(200);

        // Let's check that the template fields have been correctly updated.
        getTemplate(basePath, header, templateId, updatedTemplate, 200);
    }

    public static void deleteTemplate(String basePath, Header header, String templateId, int expectedStatusCode) {
        given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(TEXT);

        if (familyOf(expectedStatusCode) == SUCCESSFUL) {
            // Let's check that the template no longer exists.
            getTemplate(basePath, header, templateId, /* Not used */ null, 404);
        }
    }

    public static Optional<JsonObject> createInstantEmailTemplate(String basePath, Header header, InstantEmailTemplate emailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
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
            getInstantEmailTemplate(basePath, header, jsonEmailTemplate.getString("id"), emailTemplate, 200);

            return Optional.of(jsonEmailTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getInstantEmailTemplate(String basePath, Header header, String templateId, InstantEmailTemplate expectedEmailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/email/instant/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
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

    public static void deleteInstantEmailTemplate(String basePath, Header header, String templateId) {
        given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/email/instant/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the template no longer exists.
        getInstantEmailTemplate(basePath, header, templateId, /* Not used */ null, 404);
    }

    public static JsonArray getAllInstantEmailTemplates(String basePath, Header header) {
        return getAllInstantEmailTemplates(basePath, header, null);
    }

    public static JsonArray getAllInstantEmailTemplates(String basePath, Header header, String appId) {
        RequestSpecification requestSpecification = given()
                .basePath(basePath)
                .header(header);

        if (appId != null) {
            requestSpecification = requestSpecification.queryParam("applicationId", appId);
        }
        String responseBody =
                requestSpecification
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

    public static JsonObject getInstantEmailTemplatesByEventType(String basePath, Header header, String eventTypeId, boolean isExpectedToBeFound) {
        String responseBody = given()
                .basePath(basePath)
                .header(header)
                .pathParam("eventTypeId", eventTypeId)
                .when().get("/templates/email/instant/eventType/{eventTypeId}")
                .then()
                .statusCode(isExpectedToBeFound ? 200 : 404)
                .contentType(isExpectedToBeFound ? JSON : TEXT)
                .extract().asString();

        if (isExpectedToBeFound) {
            JsonObject jsonEmailTemplate = new JsonObject(responseBody);
            assertNull(jsonEmailTemplate.getJsonObject("event_type"));
            assertNull(jsonEmailTemplate.getJsonObject("subject_template"));
            assertNull(jsonEmailTemplate.getJsonObject("body_template"));

            return jsonEmailTemplate;
        }

        return null;
    }

    public static void updateInstantEmailTemplate(String basePath, Header header, String templateId, InstantEmailTemplate updatedEmailTemplate) {
        given()
                .basePath(basePath)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedEmailTemplate))
                .when().put("/templates/email/instant/{templateId}")
                .then()
                .statusCode(200);

        // Let's check that the instant email template fields have been correctly updated.
        getInstantEmailTemplate(basePath, header, templateId, updatedEmailTemplate, 200);
    }

    public static Optional<JsonObject> createAggregationEmailTemplate(String basePath, Header header, AggregationEmailTemplate emailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
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
            getAggregationEmailTemplate(basePath, header, jsonEmailTemplate.getString("id"), emailTemplate, 200);

            return Optional.of(jsonEmailTemplate);
        } else {
            return Optional.empty();
        }
    }

    public static void getAggregationEmailTemplate(String basePath, Header header, String templateId, AggregationEmailTemplate expectedEmailTemplate, int expectedStatusCode) {
        String responseBody = given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .get("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(contentTypeForStatusCode(expectedStatusCode, JSON))
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

    public static void deleteAggregationEmailTemplate(String basePath, Header header, String templateId) {
        given()
                .basePath(basePath)
                .header(header)
                .pathParam("templateId", templateId)
                .when().delete("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        // Let's check that the template no longer exists.
        getAggregationEmailTemplate(basePath, header, templateId, /* Not used */ null, 404);
    }

    public static JsonArray getAllAggregationEmailTemplates(String basePath, Header header) {
        String responseBody = given()
                .basePath(basePath)
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

    public static JsonArray getAggregationEmailTemplatesByApp(String basePath, Header header, String appId) {
        String responseBody = given()
                .basePath(basePath)
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

    public static void updateAggregationEmailTemplate(String basePath, Header header, String templateId, AggregationEmailTemplate updatedEmailTemplate) {
        given()
                .basePath(basePath)
                .header(header)
                .contentType(JSON)
                .pathParam("templateId", templateId)
                .body(Json.encode(updatedEmailTemplate))
                .when().put("/templates/email/aggregation/{templateId}")
                .then()
                .statusCode(200);

        // Let's check that the aggregation email template fields have been correctly updated.
        getAggregationEmailTemplate(basePath, header, templateId, updatedEmailTemplate, 200);
    }

    public static Template buildTemplate(String name, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription("My template");
        template.setData(data);
        return template;
    }

    public static InstantEmailTemplate buildInstantEmailTemplate(String eventTypeId, String subjectTemplateId, String bodyTemplateId) {
        InstantEmailTemplate emailTemplate = new InstantEmailTemplate();
        if (eventTypeId != null) {
            emailTemplate.setEventTypeId(UUID.fromString(eventTypeId));
        }
        emailTemplate.setSubjectTemplateId(UUID.fromString(subjectTemplateId));
        emailTemplate.setBodyTemplateId(UUID.fromString(bodyTemplateId));
        return emailTemplate;
    }

    public static AggregationEmailTemplate buildAggregationEmailTemplate(String appId, String subjectTemplateId, String bodyTemplateId) {
        AggregationEmailTemplate emailTemplate = new AggregationEmailTemplate();
        emailTemplate.setSubscriptionType(DAILY);
        if (appId != null) {
            emailTemplate.setApplicationId(UUID.fromString(appId));
        }
        emailTemplate.setSubjectTemplateId(UUID.fromString(subjectTemplateId));
        emailTemplate.setBodyTemplateId(UUID.fromString(bodyTemplateId));
        return emailTemplate;
    }

    public static String createAggregationTemplate(String basePath, String bundle, String application, ApplicationRepository applicationRepository, String adminRole) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        Template subjectTemplate = CrudTestHelpers.buildTemplate("subject-aggregation-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");
        JsonObject subjectJsonTemplate = createTemplate(basePath, adminIdentity, subjectTemplate, 200).get();
        Template bodyTemplate = CrudTestHelpers.buildTemplate("body-aggregation-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");
        JsonObject bodyJsonTemplate = createTemplate(basePath, adminIdentity, bodyTemplate, 200).get();

        Application app = applicationRepository.getApplication(bundle, application);

        AggregationEmailTemplate emailTemplate = CrudTestHelpers.buildAggregationEmailTemplate(app.getId().toString(), subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplate = createAggregationEmailTemplate(basePath, adminIdentity, emailTemplate, 200).get();
        return jsonEmailTemplate.getString("id");
    }
}

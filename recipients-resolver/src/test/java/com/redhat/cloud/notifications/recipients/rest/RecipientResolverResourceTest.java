package com.redhat.cloud.notifications.recipients.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.RecipientResolver;
import com.redhat.cloud.notifications.recipients.rest.pojo.Page;
import com.redhat.cloud.notifications.recipients.rest.pojo.RecipientQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@QuarkusTest
public class RecipientResolverResourceTest {

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @InjectMock
    RecipientResolver recipientResolver;

    @Test
    public void testInvalidParameters() throws JsonProcessingException {
        getRecipients(null, 400);
        RecipientQuery recipientQuery = new RecipientQuery();
        getRecipients(recipientQuery, 400);
        recipientQuery.setOrgId("123456");
        getRecipients(recipientQuery, 400);
        recipientQuery.setRecipientSettings(new HashSet<>());
        getRecipients(recipientQuery, 200);
        recipientQuery.setOffset(-2);
        getRecipients(recipientQuery, 400);
    }

    @Test
    public void testGetRecipients() throws JsonProcessingException {
        RecipientQuery recipentQuery = new RecipientQuery();
        recipentQuery.setRecipientSettings(new HashSet<>());
        recipentQuery.setOrgId("123456");
        Page<User> userPage = getRecipientsPage(recipentQuery);
        Assertions.assertNotNull(userPage);
        Assertions.assertEquals(0, userPage.getMeta().getCount());
        Assertions.assertEquals(0, userPage.getData().size());
        verify(recipientResolver, times(1)).findRecipients(anyString(), any(), any(), anyBoolean());

        when(recipientResolver.findRecipients(anyString(), any(), any(), anyBoolean())).thenReturn(createUserList(5));
        userPage = getRecipientsPage(recipentQuery);
        Assertions.assertEquals(5, userPage.getMeta().getCount());
        Assertions.assertEquals(5, userPage.getData().size());

        when(recipientResolver.findRecipients(anyString(), any(), any(), anyBoolean())).thenReturn(createUserList(1500));
        userPage = getRecipientsPage(recipentQuery);
        Assertions.assertEquals(1500, userPage.getMeta().getCount());
        Assertions.assertEquals(1000, userPage.getData().size());
        Assertions.assertEquals(createUserList(1000), userPage.getData());

        recipentQuery.setOffset(5);
        userPage = getRecipientsPage(recipentQuery);
        Assertions.assertEquals(1500, userPage.getMeta().getCount());
        Assertions.assertEquals(1000, userPage.getData().size());
        Assertions.assertEquals(createUserList(1000, 5), userPage.getData());

        recipentQuery.setOffset(1501);
        userPage = getRecipientsPage(recipentQuery);
        Assertions.assertEquals(1500, userPage.getMeta().getCount());
        Assertions.assertEquals(0, userPage.getData().size());

    }

    private static Page<User> getRecipientsPage(RecipientQuery resolverQuery) throws JsonProcessingException {
        return getRecipients(resolverQuery, 200).as(new TypeRef<>() { });
    }

    private static Response getRecipients(RecipientQuery resolverQuery, int expectedStatusCode) throws JsonProcessingException {
        return given()
            .when()
            .contentType(JSON)
            .body(OBJECT_MAPPER.writeValueAsString(resolverQuery))
            .post("/internal/recipient-resolver")
            .then()
            .statusCode(expectedStatusCode).extract().response();
    }

    public List<User> createUserList(int size) {
        return createUserList(size, 0);
    }

    public List<User> createUserList(int size, int initValue) {
        List<User> userList = new ArrayList<>(size);
        for (int i = initValue; i < size + initValue; i++) {
            userList.add(createUser(i));
        }
        return userList;
    }

    public User createUser(int index) {
        User user = new User();
        user.setUsername("username-" + index);
        user.setActive(true);
        user.setEmail("user email");
        user.setFirstName("user firstname");
        user.setLastName("user lastname");
        return user;
    }
}

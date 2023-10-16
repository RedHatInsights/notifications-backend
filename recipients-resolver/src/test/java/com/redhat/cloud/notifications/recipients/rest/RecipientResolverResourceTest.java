package com.redhat.cloud.notifications.recipients.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.RecipientsResolver;
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
import java.util.UUID;

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
    RecipientsResolver recipientsResolver;

    @Test
    public void testInvalidParameters() throws JsonProcessingException {
        getRecipients(null, 400);
        RecipientQuery recipientQuery = new RecipientQuery();
        getRecipients(recipientQuery, 400);
        recipientQuery.setOrgId("123456");
        getRecipients(recipientQuery, 400);
        recipientQuery.setRecipientSettings(new HashSet<>());
        getRecipients(recipientQuery, 200);
    }

    @Test
    public void testGetRecipients() throws JsonProcessingException {
        RecipientQuery recipientQuery = new RecipientQuery();
        recipientQuery.setRecipientSettings(new HashSet<>());
        recipientQuery.setOrgId("123456");
        List<User> userList = getRecipientsPage(recipientQuery);
        Assertions.assertNotNull(userList);
        Assertions.assertEquals(0, userList.size());
        verify(recipientsResolver, times(1)).findRecipients(anyString(), any(), any(), anyBoolean());

        when(recipientsResolver.findRecipients(anyString(), any(), any(), anyBoolean())).thenReturn(createUserList(500));
        userList = getRecipientsPage(recipientQuery);
        Assertions.assertEquals(500, userList.size());
    }

    private static List<User> getRecipientsPage(RecipientQuery resolverQuery) throws JsonProcessingException {
        return getRecipients(resolverQuery, 200).as(new TypeRef<>() { });
    }

    private static Response getRecipients(RecipientQuery resolverQuery, int expectedStatusCode) throws JsonProcessingException {
        return given()
            .when()
            .contentType(JSON)
            .body(OBJECT_MAPPER.writeValueAsString(resolverQuery))
            .put("/internal/recipients-resolver")
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
        user.setId(UUID.randomUUID().toString());
        user.setUsername("username-" + index);
        user.setActive(true);
        return user;
    }
}

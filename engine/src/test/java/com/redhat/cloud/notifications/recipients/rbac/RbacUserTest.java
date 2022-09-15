package com.redhat.cloud.notifications.recipients.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.routers.models.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RbacUserTest {

    static class RbacUserPage extends Page<RbacUser> {

    }

    @Test
    public void shouldDeserializeAPage() throws Exception {
        String json = "{\"meta\":{\"count\":5,\"limit\":10,\"offset\":0},\"links\":{\"first\":\"/api/rbac/v1/groups/some-id/principals/?limit=10&offset=0\",\"next\":null,\"previous\":null,\"last\":\"/api/rbac/v1/groups/some-id/principals/?limit=10&offset=0\"},\"data\":[{\"username\":\"user1\",\"email\":\"user1@somewhere\",\"first_name\":\"user1-name\",\"last_name\":null,\"is_active\":true,\"is_org_admin\":false},{\"username\":\"user2\",\"email\":\"user2@somewhere\",\"first_name\":\"user2-name\",\"last_name\":\"user2-lastname\",\"is_active\":true,\"is_org_admin\":true},{\"username\":\"user3\",\"email\":\"user3@somewhere\",\"first_name\":\"user3-name\",\"last_name\":null,\"is_active\":true,\"is_org_admin\":true},{\"username\":\"user4\",\"email\":\"user4@somewhere\",\"first_name\": null,\"last_name\":null,\"is_active\":true,\"is_org_admin\":true},{\"username\":\"user5\",\"email\":\"user5@somewhere\",\"first_name\":\"user5-name\",\"last_name\":null,\"is_active\":false,\"is_org_admin\":true}]}";
        ObjectMapper mapper = new ObjectMapper();

        RbacUserPage page = mapper.readValue(json, RbacUserPage.class);
        assertEquals(Long.valueOf(5), page.getMeta().getCount());

        testRbacUser(page.getData().get(0), "user1", "user1@somewhere", "user1-name", null, true, false);
        testRbacUser(page.getData().get(1), "user2", "user2@somewhere", "user2-name", "user2-lastname", true, true);
        testRbacUser(page.getData().get(2), "user3", "user3@somewhere", "user3-name", null, true, true);
        testRbacUser(page.getData().get(3), "user4", "user4@somewhere", null, null, true, true);
        testRbacUser(page.getData().get(4), "user5", "user5@somewhere", "user5-name", null, false, true);
    }

    private void testRbacUser(RbacUser user, String username, String email, String firstName, String lastName, boolean isActive, boolean isOrgadmin) {
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertEquals(isActive, user.getActive());
        assertEquals(isOrgadmin, user.getOrgAdmin());
    }

}

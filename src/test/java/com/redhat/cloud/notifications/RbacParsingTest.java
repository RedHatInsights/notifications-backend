package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.auth.RbacRaw;
import org.junit.jupiter.api.Test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Test RBAC parsing.
 * Brought over from policies-ui-backend and extended
 */
public class RbacParsingTest {

    private static final String BASE = "src/test/resources/rbac-examples/";

    @Test
    void testParseExample() throws Exception {
        File file = new File(BASE + "rbac_example.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);
        assert rbac.data.size() == 2;

        assert rbac.canWrite("bar", "resname");
        assert !rbac.canRead("resname");
        assert !rbac.canWrite("*");
        assert !rbac.canWrite("no-perm");

    }

    @Test
    void testNoAccess() throws Exception {
        File file = new File(BASE + "rbac_example_no_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert !rbac.canRead("*");
        assert !rbac.canWrite("*");
    }

    @Test
    void testFullAccess() throws Exception {
        File file = new File(BASE + "rbac_example_full_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert rbac.canRead("notifications");
        assert !rbac.canRead("dummy");
        assert rbac.canWrite("notifications");
        assert rbac.canWrite("integrations", "endpoints");
        assert rbac.canWrite("integrations", "does-not-exist");
        assert !rbac.canWrite("dummy");
    }

    @Test
    void testPartialAccess() throws FileNotFoundException {
        File file = new File(BASE + "rbac_example_partial_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert rbac.canRead("policies");
        assert !rbac.canRead("dummy");
        assert !rbac.canWrite("policies");
        assert !rbac.canWrite("dummy");
        assert rbac.canDo("policies", "execute");
        assert rbac.canDo("policies", "*", "execute");
        assert !rbac.canDo("policies", "*", "list");
    }

    @Test
    void testTwoApps() throws FileNotFoundException {
        File file = new File(BASE + "rbac_example_two_apps1.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert !rbac.canRead("notifications");
        assert !rbac.canWrite("notifications");
        assert rbac.canDo("notifications", "execute");

        assert rbac.canRead("integrations", "endpoints");
        assert !rbac.canWrite("integrations", "endpoints");
        // We have no * item
        assert !rbac.canRead("integrations");
        assert !rbac.canWrite("integrations");

        assert !rbac.canDo("integrations", "read");
        assert !rbac.canDo("integrations", "execute");
        assert rbac.canDo("integrations", "admin", "execute");
    }
}

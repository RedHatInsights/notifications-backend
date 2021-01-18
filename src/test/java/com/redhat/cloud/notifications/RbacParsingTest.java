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
    public static final String ALL = "*";

    @Test
    void testParseExample() throws Exception {
        File file = new File(BASE + "rbac_example.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);
        assert rbac.data.size() == 2;

        assert rbac.canWrite("bar", "resname");
        assert !rbac.canRead("resname", ALL);
        assert !rbac.canWrite(ALL, ALL);
        assert !rbac.canWrite("no-perm", ALL);

    }

    @Test
    void testNoAccess() throws Exception {
        File file = new File(BASE + "rbac_example_no_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert !rbac.canRead(ALL, ALL);
        assert !rbac.canWrite(ALL, ALL);
    }

    @Test
    void testFullAccess() throws Exception {
        File file = new File(BASE + "rbac_example_full_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert rbac.canRead("notifications", ALL);
        assert !rbac.canRead("dummy", ALL);
        assert rbac.canWrite("notifications", ALL);
        assert rbac.canWrite("integrations", "endpoints");
        assert rbac.canWrite("integrations", "does-not-exist");
        assert !rbac.canWrite("dummy", ALL);
    }

    @Test
    void testNIRead() throws Exception {
        File file = new File(BASE + "rbac_n_i_read.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert rbac.canRead("notifications", "notifications");
        assert rbac.canDo("notifications", "notifications", "read");
        assert !rbac.canRead("notifications", ALL);
        assert !rbac.canRead("notifications", "does-not-exist");
        assert !rbac.canWrite("notifications", ALL);
        assert !rbac.canRead("dummy", ALL);
        assert rbac.canRead("integrations", "endpoints");
        assert !rbac.canRead("integrations", "does-not-exist");
        assert !rbac.canWrite("integrations", "does-not-exist");
        assert !rbac.canWrite("dummy", ALL);
    }

    @Test
    void testPartialAccess() throws FileNotFoundException {
        File file = new File(BASE + "rbac_example_partial_access.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert rbac.canRead("policies", ALL);
        assert !rbac.canRead("dummy", ALL);
        assert !rbac.canWrite("policies", ALL);
        assert !rbac.canWrite("dummy", ALL);
        assert rbac.canDo("policies", ALL, "execute");
        assert !rbac.canDo("policies", ALL, "list");
    }

    @Test
    void testTwoApps() throws FileNotFoundException {
        File file = new File(BASE + "rbac_example_two_apps1.json");
        Jsonb jb = JsonbBuilder.create();
        RbacRaw rbac = jb.fromJson(new FileInputStream(file), RbacRaw.class);

        assert !rbac.canRead("notifications", ALL);
        assert !rbac.canWrite("notifications", ALL);
        assert rbac.canDo("notifications", ALL, "execute");

        assert rbac.canRead("integrations", "endpoints");
        assert !rbac.canWrite("integrations", "endpoints");
        // We have no * item
        assert !rbac.canRead("integrations", ALL);
        assert !rbac.canWrite("integrations", ALL);

        assert !rbac.canDo("integrations", ALL, "read");
        assert !rbac.canDo("integrations", ALL, "execute");
        assert rbac.canDo("integrations", "admin", "execute");
    }
}

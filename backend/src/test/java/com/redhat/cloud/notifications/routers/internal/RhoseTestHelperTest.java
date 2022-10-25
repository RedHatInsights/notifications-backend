package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.ProdTestProfile;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ProdTestProfile.class)
public class RhoseTestHelperTest {

    @Test
    void helperShouldNotBeAvailableWithProdProfile() {

        // The 'prod' profile should be active.
        assertEquals("prod", ProfileManager.getActiveProfile());

        // RhoseTestHelper should NOT be exposed when the 'prod' profile is active.
        when()
                .get(RhoseTestHelper.BASE_PATH)
                .then()
                .statusCode(404);
    }
}

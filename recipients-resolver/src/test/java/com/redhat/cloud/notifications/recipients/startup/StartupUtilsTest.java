package com.redhat.cloud.notifications.recipients.startup;

import com.redhat.cloud.notifications.recipients.statup.StartupUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
public class StartupUtilsTest {

    @Inject
    StartupUtils startupUtils;

    @Test
    void readKeystoreTest() {
        String resourceName = "testKeystore.jks";
        String testPassword = "change_it";

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(resourceName).getFile());

        List<String> certificateData = startupUtils.readKeystore(Optional.of(file.getAbsolutePath()), Optional.of(testPassword));
        assertFalse(certificateData.isEmpty());
        assertEquals("Certificate 'testexpcertif' is about to expire! (on Mon Sep 02 10:12:19 UTC 2024)", certificateData.get(0));
    }
}

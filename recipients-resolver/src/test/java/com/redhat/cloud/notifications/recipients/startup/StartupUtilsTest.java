package com.redhat.cloud.notifications.recipients.startup;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import com.redhat.cloud.notifications.recipients.statup.StartupUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
public class StartupUtilsTest {

    @Inject
    StartupUtils startupUtils;

    @InjectMock
    RecipientsResolverConfig recipientsResolverConfig;

    @Test
    void readKeystoreTest() {
        String resourceName = "testKeystore.jks";
        String testPassword = "change_it";

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(resourceName).getFile());
        URI fileUri = file.toURI();

        when(recipientsResolverConfig.getQuarkusItServiceKeystore()).thenReturn(Optional.of(fileUri));
        when(recipientsResolverConfig.getQuarkusItServicePassword()).thenReturn(Optional.of(testPassword));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertEquals("@channel Certificate 'testexpcertif' is about to expire! (on Mon Sep 02 10:12:19 UTC 2024)", certificateData.get(0));
    }
}

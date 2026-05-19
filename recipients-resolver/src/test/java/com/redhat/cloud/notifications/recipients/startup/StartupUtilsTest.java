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
        ClassLoader classLoader = getClass().getClassLoader();
        String testPassword = "change_it";
        when(recipientsResolverConfig.getQuarkusItServicePassword()).thenReturn(Optional.of(testPassword));

        // JKS
        String resourceNameJks = "testKeystore.jks";
        File fileJks = new File(classLoader.getResource(resourceNameJks).getFile());
        URI fileUriJks = fileJks.toURI();

        when(recipientsResolverConfig.getQuarkusItServiceKeystore()).thenReturn(Optional.of(fileUriJks));
        when(recipientsResolverConfig.getQuarkusItServiceKeystoreType()).thenReturn("JKS");
        List<String> certificateDataJks = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateDataJks.size());
        assertEquals("(@channel) Certificate 'testexpcertif' has expired since Mon Sep 02 10:12:19 UTC 2024", certificateDataJks.get(0));

        // PKCS #12
        String resourceNameP12 = "testKeystore.p12";
        File fileP12 = new File(classLoader.getResource(resourceNameJks).getFile());
        URI fileUriP12 = fileP12.toURI();

        when(recipientsResolverConfig.getQuarkusItServiceKeystore()).thenReturn(Optional.of(fileUriP12));
        when(recipientsResolverConfig.getQuarkusItServiceKeystoreType()).thenReturn("P12");
        List<String> certificateDataP12 = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateDataP12.size());
        assertEquals("(@channel) Certificate 'testexpcertif' has expired since Mon Sep 02 10:12:19 UTC 2024", certificateDataP12.get(0));
    }
}

package com.redhat.cloud.notifications.recipients.startup;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import com.redhat.cloud.notifications.recipients.statup.StartupUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.io.File;
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
    void readCertificateTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("testCert.pem").getFile());

        when(recipientsResolverConfig.getItServicesTlsCertPath()).thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeyStorePath()).thenReturn(Optional.empty());
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertEquals("(@channel) Certificate [0] 'CN=Notifications,OU=Unknown,O=Red Hat,L=Unknown,ST=Unknown,C=Unknown' has expired since Mon Sep 02 10:12:19 UTC 2024", certificateData.get(0));
    }

    @Test
    void readKeystoreTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("testKeystore.jks").getFile());

        when(recipientsResolverConfig.getItServicesTlsCertPath()).thenReturn(Optional.empty());
        when(recipientsResolverConfig.getItServicesKeyStorePath()).thenReturn(Optional.of(file.toURI().toString()));
        when(recipientsResolverConfig.getItServicesKeyStorePassword()).thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertEquals("(@channel) Certificate 'testexpcertif' has expired since Mon Sep 02 10:12:19 UTC 2024", certificateData.get(0));
    }
}

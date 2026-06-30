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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class StartupUtilsTest {

    @Inject
    StartupUtils startupUtils;

    @InjectMock
    RecipientsResolverConfig recipientsResolverConfig;

    @Test
    void readP12KeystoreTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath()).thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword()).thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        String message = certificateData.get(0);
        assertTrue(message.startsWith("(@channel) Certificate 'testexpcertif' has expired since "));
        assertTrue(message.contains("UTC"));
        assertTrue(message.length() > 60, "Message should contain full date: " + message);
    }

    @Test
    void readJksKeystoreTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.jks");
        assertNotNull(resource, "Missing test resource: testKeystore.jks");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath()).thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword()).thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        String message = certificateData.get(0);
        assertTrue(message.startsWith("(@channel) Certificate 'testexpcertif' has expired since "));
        assertTrue(message.contains("UTC"));
        assertTrue(message.length() > 60, "Message should contain full date: " + message);
    }

    @Test
    void readJksKeystoreWithFileUriPrefixTest() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.jks");
        assertNotNull(resource, "Missing test resource: testKeystore.jks");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath()).thenReturn(Optional.of("file:" + file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword()).thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        String message = certificateData.get(0);
        assertTrue(message.startsWith("(@channel) Certificate 'testexpcertif' has expired since "));
        assertTrue(message.contains("UTC"));
        assertTrue(message.length() > 60, "Message should contain full date: " + message);
    }

    @Test
    void readP12KeystoreNotConfiguredTest() {
        when(recipientsResolverConfig.getItServicesKeystorePath()).thenReturn(Optional.empty());
        when(recipientsResolverConfig.getItServicesKeystorePassword()).thenReturn(Optional.empty());
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertEquals("IT services keystore path is not configured", certificateData.get(0));
    }

    @Test
    void shouldReturnNotConfiguredMessageWhenKeystorePathIsBlank() {
        when(recipientsResolverConfig.getItServicesKeystorePath()).thenReturn(Optional.of("   "));
        when(recipientsResolverConfig.getItServicesKeystorePassword()).thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertEquals("IT services keystore path is not configured", certificateData.get(0));
    }

    @Test
    void shouldHandleFileNotFoundWithFileUriPrefix() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("file:/non/existent/path/keystore.p12"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertTrue(certificateData.get(0).contains("keystore file not found"));
    }

    @Test
    void shouldHandleFileNotFoundWithoutUriPrefix() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("/non/existent/path/keystore.p12"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));
        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertTrue(certificateData.get(0).contains("keystore file not found"));
    }

    @Test
    void shouldThrowRuntimeExceptionOnInvalidPassword() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("wrong_password"));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("Failed to load IT services keystore"));
    }

    @Test
    void shouldHandleFileNotFoundWithUppercaseJksExtension() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.jks");
        assertNotNull(resource, "Missing test resource: testKeystore.jks");
        File file = new File(resource.getFile());
        // Create a path with uppercase extension (simulated via string manipulation)
        String uppercasePath = file.getAbsolutePath().replace(".jks", ".JKS");

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(uppercasePath));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertTrue(certificateData.get(0).contains("keystore file not found"));
    }

    @Test
    void shouldHandleFileUriWithTripleSlash() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.jks");
        assertNotNull(resource, "Missing test resource: testKeystore.jks");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("file://" + file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        String message = certificateData.get(0);
        assertTrue(message.startsWith("(@channel) Certificate 'testexpcertif' has expired since "));
        assertTrue(message.contains("UTC"));
        assertTrue(message.length() > 60, "Message should contain full date: " + message);
    }

    @Test
    void shouldHandleFileNotFoundForPfxExtension() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("/path/to/keystore.pfx"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        List<String> certificateData = startupUtils.checkCertificatesExpiration();
        assertEquals(1, certificateData.size());
        assertTrue(certificateData.get(0).contains("keystore file not found"));
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenPasswordIsMissingAndItServiceIsOnlySource() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.empty());
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled(null)).thenReturn(false);
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled(null)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("password is missing or empty"));
        assertTrue(exception.getMessage().contains("IT_SERVICE_TO_SERVICE_SECRET_NAME"));
    }

    @Test
    void shouldWarnButNotFailWhenPasswordIsMissingAndMbopEnabled() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.empty());
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled(null)).thenReturn(true);
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled(null)).thenReturn(false);

        List<String> result = startupUtils.checkCertificatesExpiration();
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("alternative user sources enabled"));
    }

    @Test
    void shouldWarnButNotFailWhenPasswordIsMissingAndRbacEnabled() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.empty());
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled(null)).thenReturn(false);
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled(null)).thenReturn(true);

        List<String> result = startupUtils.checkCertificatesExpiration();
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("alternative user sources enabled"));
    }

    @Test
    void shouldWarnButNotFailWhenPasswordIsMissingAndBothMbopAndRbacEnabled() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.empty());
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled(null)).thenReturn(true);
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled(null)).thenReturn(true);

        List<String> result = startupUtils.checkCertificatesExpiration();
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("alternative user sources enabled"));
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenPasswordIsBlankAndItServiceIsOnlySource() {
        ClassLoader classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("testKeystore.p12");
        assertNotNull(resource, "Missing test resource: testKeystore.p12");
        File file = new File(resource.getFile());

        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of(file.getAbsolutePath()));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("   "));
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled(null)).thenReturn(false);
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled(null)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("password is missing or empty"));
    }

    @Test
    void shouldHandleUnknownExtensionWhenFileNotFound() {
        // Since we can't create a file with unknown extension in test resources,
        // this test verifies file not found handling for unknown extensions
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("/path/to/keystore.unknown"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("Unknown keystore file extension"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForMissingExtension() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("/path/to/keystorewithnoextension"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("No file extension found"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForTooShortFileUri() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("file:"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("Invalid file URI (too short)"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForMalformedFileUri() {
        when(recipientsResolverConfig.getItServicesKeystorePath())
            .thenReturn(Optional.of("file:invalid uri with spaces"));
        when(recipientsResolverConfig.getItServicesKeystorePassword())
            .thenReturn(Optional.of("change_it"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> startupUtils.checkCertificatesExpiration());
        assertTrue(exception.getMessage().contains("Invalid file URI"));
    }
}

package com.redhat.cloud.notifications.recipients.statup;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class StartupUtils {

    public static final Pattern ACCESS_LOG_FILTER_PATTERN = Pattern.compile(".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?");

    private static final DateTimeFormatter CERT_EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @ConfigProperty(name = "quarkus.http.access-log.category")
    String accessLogCategory;

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    public void initAccessLogFilter() {
        java.util.logging.Logger.getLogger(accessLogCategory).setFilter(logRecord ->
                !ACCESS_LOG_FILTER_PATTERN.matcher(logRecord.getMessage()).matches()
        );
    }

    public void logGitProperties() {
        try {
            Log.info(readGitProperties());
        } catch (Exception e) {
            Log.error("Could not read git.properties", e);
        }
    }

    public String readGitProperties() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (inputStream == null) {
                return "git.properties is not available";
            } else {
                StringBuilder result = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.startsWith("#Generated")) {
                            result.append(line);
                        }
                    }
                }
                return result.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> checkCertificatesExpiration() {
        Optional<String> keystorePath = recipientsResolverConfig.getItServicesKeystorePath();
        Optional<String> keystorePassword = recipientsResolverConfig.getItServicesKeystorePassword();

        if (keystorePath.isPresent() && !keystorePath.get().isBlank()) {
            if (keystorePassword.isEmpty() || keystorePassword.get().isBlank()) {
                // IT service config is only fatal when both MBOP and RBAC toggles are disabled
                // (i.e., IT service is the only user source)
                boolean mbopEnabled = recipientsResolverConfig.isFetchUsersWithMbopEnabled(null);
                boolean rbacEnabled = recipientsResolverConfig.isFetchUsersWithRbacEnabled(null);
                if (!mbopEnabled && !rbacEnabled) {
                    String errorMessage = "IT services keystore path is configured but password is missing or empty. " +
                            "Cannot establish mTLS connection. Check IT_SERVICE_TO_SERVICE_SECRET_NAME and " +
                            "IT_SERVICE_TO_SERVICE_PASSWORD_KEY configuration.";
                    Log.fatal(errorMessage);
                    throw new IllegalStateException(errorMessage);
                } else {
                    Log.warn("IT services keystore path is configured but password is missing or empty. " +
                            "This may cause keystore loading to fail. IT service is not required since MBOP or RBAC toggles are enabled.");
                    List<String> result = new ArrayList<>();
                    result.add("IT services keystore password not configured (non-fatal: alternative user sources enabled)");
                    return result;
                }
            }
            return checkKeystoreCertExpiration(
                keystorePath.get(),
                keystorePassword.get()
            );
        }

        List<String> result = new ArrayList<>();
        result.add("IT services keystore path is not configured");
        return result;
    }

    private List<String> checkKeystoreCertExpiration(String keystorePath, String password) {
        List<String> result = new ArrayList<>();
        char[] passwordChars = password.toCharArray();
        try {
            String actualPath = normalizeKeystorePath(keystorePath);
            String keystoreType = detectKeystoreType(actualPath);

            File keystoreFile = new File(actualPath);
            if (!keystoreFile.exists()) {
                String logMessage = String.format("IT services keystore file not found: %s", keystorePath);
                Log.warn(logMessage);
                result.add(logMessage);
                Arrays.fill(passwordChars, '\0');
                return result;
            }

            Log.infof("Loading IT services keystore from %s (detected type: %s)", keystorePath, keystoreType);

            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                keyStore.load(fis, passwordChars);
            }

            return checkCertificateExpiry(keyStore, keystoreType, keystorePath);
        } catch (IllegalArgumentException | URISyntaxException e) {
            String errorMessage = e instanceof URISyntaxException
                ? String.format("Invalid file URI for keystore path: '%s'", keystorePath)
                : e.getMessage();
            Log.error(errorMessage, e);
            throw new IllegalArgumentException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = String.format("Failed to load IT services keystore from %s", keystorePath);
            Log.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private String normalizeKeystorePath(String keystorePath) throws URISyntaxException {
        if (!keystorePath.startsWith("file:")) {
            return keystorePath;
        }
        if (keystorePath.length() <= 5) {
            throw new IllegalArgumentException(String.format("Invalid file URI (too short): %s", keystorePath));
        }
        return new URI(keystorePath).getPath();
    }

    private String detectKeystoreType(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0 || lastDot == path.length() - 1) {
            throw new IllegalArgumentException(String.format("No file extension found for keystore path '%s'. " +
                    "Supported extensions: .jks, .p12, .pfx", path));
        }
        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
        if (lowerPath.endsWith(".jks")) {
            return "JKS";
        } else if (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")) {
            return "PKCS12";
        }
        String extension = path.substring(lastDot);
        throw new IllegalArgumentException(String.format("Unknown keystore file extension '%s' for path '%s'. " +
                "Supported extensions: .jks, .p12, .pfx", extension, path));
    }

    private List<String> checkCertificateExpiry(KeyStore keyStore, String keystoreType, String keystorePath)
            throws KeyStoreException {
        List<String> result = new ArrayList<>();
        ZonedDateTime currentUtcTime = ZonedDateTime.now(UTC);
        Enumeration<String> aliases = keyStore.aliases();
        boolean foundCertificate = false;
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate cert)) {
                continue;
            }
            foundCertificate = true;
            Date notAfter = cert.getNotAfter();
            ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), UTC);
            String utcExpDateTimeStr = CERT_EXPIRY_FORMATTER.format(utcExpDateTime);
            long diff = ChronoUnit.DAYS.between(currentUtcTime, utcExpDateTime);
            String logMessage;
            if (diff < 0) {
                logMessage = String.format("(@channel) Certificate '%s' has expired since %s", alias, utcExpDateTimeStr);
                Log.fatal(logMessage);
            } else if (diff < 10) {
                logMessage = String.format("(@channel) Certificate '%s' is about to expire! (on %s)", alias, utcExpDateTimeStr);
                Log.fatal(logMessage);
            } else if (diff < 30) {
                logMessage = String.format("(@channel) Certificate '%s' will expire within 30 days! (on %s)", alias, utcExpDateTimeStr);
                Log.error(logMessage);
            } else if (diff < 60) {
                logMessage = String.format("(@channel) Certificate '%s' will expire within 60 days! (on %s)", alias, utcExpDateTimeStr);
                Log.warn(logMessage);
            } else {
                logMessage = String.format("Certificate '%s' will expire on %s", alias, utcExpDateTimeStr);
                Log.info(logMessage);
            }
            result.add(logMessage);
        }
        if (!foundCertificate) {
            String logMessage = String.format("IT services %s keystore contains no X.509 certificates: %s", keystoreType, keystorePath);
            Log.fatal(logMessage);
            throw new RuntimeException(logMessage);
        }
        return result;
    }
}

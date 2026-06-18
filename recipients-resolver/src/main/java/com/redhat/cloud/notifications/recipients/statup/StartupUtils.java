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
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class StartupUtils {

    public static final Pattern ACCESS_LOG_FILTER_PATTERN = Pattern.compile(".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?");

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
        Optional<String> certPath = recipientsResolverConfig.getItServicesTlsCertPath();
        if (certPath.isPresent() && !certPath.get().isBlank()) {
            return checkPemCertExpiration(certPath.get());
        }
        Optional<String> keystorePath = recipientsResolverConfig.getItServicesKeyStorePath();
        if (keystorePath.isPresent() && !keystorePath.get().isBlank()) {
            return checkJksCertExpiration(
                keystorePath.get(),
                recipientsResolverConfig.getItServicesKeyStorePassword().orElse("")
            );
        }
        List<String> result = new ArrayList<>();
        result.add("IT services TLS certificate path is not configured");
        return result;
    }

    private List<String> checkPemCertExpiration(String certPath) {
        List<String> result = new ArrayList<>();
        File certFile = new File(certPath);
        if (!certFile.exists()) {
            String logMessage = String.format("IT services TLS certificate file not found: %s", certPath);
            Log.warn(logMessage);
            result.add(logMessage);
            return result;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(certFile)) {
                Collection<? extends Certificate> certs = cf.generateCertificates(fis);
                if (certs.isEmpty()) {
                    String logMessage = String.format("IT services TLS certificate file contains no valid PEM blocks: %s", certPath);
                    Log.warn(logMessage);
                    result.add(logMessage);
                    return result;
                }
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
                final ZonedDateTime currentUtcTime = ZonedDateTime.now(ZoneId.of("UTC"));
                int index = 0;
                for (Certificate cert : certs) {
                    Date notAfter = ((X509Certificate) cert).getNotAfter();
                    ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), ZoneId.of("UTC"));
                    final String utcExpDateTimeStr = formatter.format(utcExpDateTime);
                    String subject = ((X509Certificate) cert).getSubjectX500Principal().getName();
                    long diff = ChronoUnit.DAYS.between(currentUtcTime, utcExpDateTime);
                    String logMessage;
                    if (diff < 0) {
                        logMessage = String.format("(@channel) Certificate [%d] '%s' has expired since %s", index, subject, utcExpDateTimeStr);
                        Log.fatal(logMessage);
                    } else if (diff < 10) {
                        logMessage = String.format("(@channel) Certificate [%d] '%s' is about to expire! (on %s)", index, subject, utcExpDateTimeStr);
                        Log.fatal(logMessage);
                    } else if (diff < 30) {
                        logMessage = String.format("(@channel) Certificate [%d] '%s' will expire within 30 days! (on %s)", index, subject, utcExpDateTimeStr);
                        Log.error(logMessage);
                    } else if (diff < 60) {
                        logMessage = String.format("(@channel) Certificate [%d] '%s' will expire within 60 days! (on %s)", index, subject, utcExpDateTimeStr);
                        Log.warn(logMessage);
                    } else {
                        logMessage = String.format("Certificate [%d] '%s' will expire on %s", index, subject, utcExpDateTimeStr);
                        Log.info(logMessage);
                    }
                    result.add(logMessage);
                    index++;
                }
            }
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private List<String> checkJksCertExpiration(String keystoreUri, String password) {
        List<String> result = new ArrayList<>();
        try {
            File keystoreFile = new File(new URI(keystoreUri));
            if (!keystoreFile.exists()) {
                String logMessage = String.format("IT services JKS keystore file not found: %s", keystoreUri);
                Log.warn(logMessage);
                result.add(logMessage);
                return result;
            }
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                keyStore.load(fis, password.toCharArray());
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
            ZonedDateTime currentUtcTime = ZonedDateTime.now(ZoneId.of("UTC"));
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
                ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), ZoneId.of("UTC"));
                String utcExpDateTimeStr = formatter.format(utcExpDateTime);
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
                String logMessage = String.format("IT services JKS keystore contains no X.509 certificates: %s", keystoreUri);
                Log.warn(logMessage);
                result.add(logMessage);
            }
        } catch (URISyntaxException | KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}

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
        List<String> result = new ArrayList<>();
        String logMessage;
        if (certPath.isEmpty() || certPath.get().isBlank()) {
            result.add("IT services TLS certificate path is not configured");
            return result;
        }
        File certFile = new File(certPath.get());
        if (!certFile.exists()) {
            logMessage = String.format("IT services TLS certificate file not found: %s", certPath.get());
            Log.warn(logMessage);
            result.add(logMessage);
            return result;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(certFile)) {
                Collection<? extends Certificate> certs = cf.generateCertificates(fis);
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
                final ZonedDateTime currentUtcTime = ZonedDateTime.now(ZoneId.of("UTC"));
                int index = 0;
                for (Certificate cert : certs) {
                    Date notAfter = ((X509Certificate) cert).getNotAfter();
                    ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), ZoneId.of("UTC"));
                    final String utcExpDateTimeStr = formatter.format(utcExpDateTime);
                    String subject = ((X509Certificate) cert).getSubjectX500Principal().getName();
                    long diff = ChronoUnit.DAYS.between(currentUtcTime, utcExpDateTime);
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
}

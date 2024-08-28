package com.redhat.cloud.notifications.recipients.statup;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class StartupUtils {

    public static final Pattern ACCESS_LOG_FILTER_PATTERN = Pattern.compile(".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?");

    @ConfigProperty(name = "quarkus.http.access-log.category")
    String accessLogCategory;

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

    public List<String> readKeystore(Optional<String> keystoreFile, Optional<String> keystorePassword) {
        List<String> result = new ArrayList<>();
        String logMessage;
        if (keystoreFile.isEmpty() || keystorePassword.isEmpty()) {
            result.add("keystore file or password is empty");
            return result;
        }
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(keystoreFile.get()), keystorePassword.get().toCharArray());

            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
            final ZonedDateTime currentUtcTime = ZonedDateTime.now(ZoneId.of("UTC"));

            Iterator<String> aliasesIt = ks.aliases().asIterator();
            while (aliasesIt.hasNext()) {
                String alias = aliasesIt.next();
                Date notAfter = ((X509Certificate) ks.getCertificate(alias)).getNotAfter();

                ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), ZoneId.of("UTC"));

                final String utcExpDateTimeStr = formatter.format(utcExpDateTime);

                long diff = ChronoUnit.DAYS.between(currentUtcTime, utcExpDateTime);
                if (diff < 10) {
                    logMessage = String.format("Certificate '%s' is about to expire! (on %s)", alias, utcExpDateTimeStr);
                    Log.fatalf(logMessage);
                } else if (diff < 30) {
                    logMessage = String.format("Certificate '%s' will expire within 30 days! (on %s)", alias, utcExpDateTimeStr);
                    Log.errorf(logMessage);
                } else if (diff < 60) {
                    logMessage = String.format("Certificate '%s' will expire within 60 days! (on %s)", alias, utcExpDateTimeStr);
                    Log.warnf(logMessage);
                } else {
                    logMessage = String.format("Certificate '%s' will expire on %s", alias, utcExpDateTimeStr);
                    Log.warnf(logMessage);
                }
                result.add(logMessage);
            }
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}

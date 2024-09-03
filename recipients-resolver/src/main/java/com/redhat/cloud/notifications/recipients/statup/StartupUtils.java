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
        Optional<URI> keystoreFile = recipientsResolverConfig.getQuarkusItServiceKeystore();
        Optional<String> keystorePassword = recipientsResolverConfig.getQuarkusItServicePassword();
        List<String> result = new ArrayList<>();
        String logMessage;
        if (keystoreFile.isEmpty() || keystorePassword.isEmpty()) {
            result.add("keystore file or password is empty");
            return result;
        }
        try {
            File f = new File(keystoreFile.get());
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream keystoreFileInputStream = new FileInputStream(f)) {

                ks.load(keystoreFileInputStream, keystorePassword.get().toCharArray());

                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
                final ZonedDateTime currentUtcTime = ZonedDateTime.now(ZoneId.of("UTC"));

                Iterator<String> aliasesIt = ks.aliases().asIterator();
                while (aliasesIt.hasNext()) {
                    String alias = aliasesIt.next();
                    Date notAfter = ((X509Certificate) ks.getCertificate(alias)).getNotAfter();

                    ZonedDateTime utcExpDateTime = ZonedDateTime.ofInstant(notAfter.toInstant(), ZoneId.of("UTC"));

                    final String utcExpDateTimeStr = formatter.format(utcExpDateTime);

                    long diff = ChronoUnit.DAYS.between(currentUtcTime, utcExpDateTime);
                    if (diff < 0) {
                        logMessage = String.format("Certificate '%s' has expired since %s", alias, utcExpDateTimeStr);
                        Log.fatal(logMessage);
                    } else if (diff < 10) {
                        logMessage = String.format("Certificate '%s' is about to expire! (on %s)", alias, utcExpDateTimeStr);
                        Log.fatal(logMessage);
                    } else if (diff < 30) {
                        logMessage = String.format("Certificate '%s' will expire within 30 days! (on %s)", alias, utcExpDateTimeStr);
                        Log.error(logMessage);
                    } else if (diff < 60) {
                        logMessage = String.format("Certificate '%s' will expire within 60 days! (on %s)", alias, utcExpDateTimeStr);
                        Log.warn(logMessage);
                    } else {
                        logMessage = String.format("Certificate '%s' will expire on %s", alias, utcExpDateTimeStr);
                        Log.info(logMessage);
                    }
                    result.add(logMessage);
                }
            }
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}

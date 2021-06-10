package com.redhat.cloud.notifications;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.redhat.cloud.notifications.NotificationsApp.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationsAppTest {

    final String logMessage = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /q/health HTTP/1.1\" 200 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";

    @Test
    void shouldNotMachWhenInputEndsWithNewLine() {
        assertFalse(Pattern.matches(FILTER_REGEX, logMessage));
    }

    @Test
    void shouldMachWhenInputEndsWithNewLine() {
        assertTrue(Pattern.matches(FILTER_REGEX, logMessage.trim()));
    }

    @Test
    void shouldMatchWhenHttpVersionIsTwo() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /q/health HTTP/2.0\" 200 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertTrue(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }

    @Test
    void shouldMatchWithMetrics() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /metrics HTTP/1.1\" 200 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertTrue(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }

    @Test
    void shouldMatchWithQMetrics() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /q/metrics HTTP/1.1\" 200 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertTrue(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }

    @Test
    void shouldNotMatchWithSomethingelse() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /my-own-api-endpoint HTTP/1.1\" 200 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertFalse(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }

    @Test
    void shouldNotMatchWhenHttpStatusIs500() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /q/health HTTP/2.0\" 500 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertFalse(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }

    @Test
    void shouldNotMatchWhenHttpStatusIs404() {
        String inputWithHttpTwoZero = "127.0.0.1 - - 09/Jun/2021:16:07:07 +0200 \"GET /q/health HTTP/2.0\" 404 46 \"-\" \"Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0\"\n";
        assertFalse(Pattern.matches(FILTER_REGEX, inputWithHttpTwoZero.trim()));
    }
}

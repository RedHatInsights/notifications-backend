package com.redhat.cloud.notifications.avro;


import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public class Iso8601ConversionTest {

    @Test
    void fromStringISO8601() {
        Iso8601Conversion conversion = new Iso8601Conversion();

        Assert.assertEquals(
                LocalDateTime.of(
                        2020,
                        7,
                        14,
                        13,
                        22,
                        10
                ),
                conversion.fromCharSequence("2020-07-14T13:22:10Z", null, null)
        );
    }

    @Test
    void fromStringISO8601WithNano() {
        Iso8601Conversion conversion = new Iso8601Conversion();

        Assert.assertEquals(
                LocalDateTime.of(
                        2020,
                        7,
                        14,
                        13,
                        22,
                        10,
                        133000000
                ),
                conversion.fromCharSequence("2020-07-14T13:22:10.133", null, null)
        );
    }

    @Test
    void toStringISO8601() {
        Iso8601Conversion conversion = new Iso8601Conversion();

        Assert.assertEquals(
                "2020-07-14T13:22:10",
                conversion.toCharSequence(LocalDateTime.of(
                        2020,
                        7,
                        14,
                        13,
                        22,
                        10
                ), null, null)
        );
    }

    @Test
    void toStringISO8601WithNano() {
        Iso8601Conversion conversion = new Iso8601Conversion();

        Assert.assertEquals(
                "2020-07-14T13:22:10.133",
                conversion.toCharSequence(LocalDateTime.of(
                        2020,
                        7,
                        14,
                        13,
                        22,
                        10,
                        133000000
                ), null, null)
        );
    }

}

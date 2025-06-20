package com.redhat.cloud.notifications.qute.templates.extensions;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class ErrataSortExtensionTest {

    @Test
    void testSortErrataSecurityArray() {
        Map<String, Object> errata1 = Map.of("id", "RHSA-2024:0315", "severity", "none");
        Map<String, Object> errata2 = Map.of("id", "RHSA-2022:1215", "severity", "Low");
        Map<String, Object> errata3 = Map.of("id", "RHSA-2025:0315", "severity", "Important");
        Map<String, Object> errata4 = Map.of("id", "RHSA-2023:001", "severity", "Moderate");
        Map<String, Object> errata5 = Map.of("id", "RHSA-2124:0315", "severity", "Important");
        Map<String, Object> errata6 = Map.of("id", "RHSA-1001:1315", "severity", "Important");
        Map<String, Object> errata7 = Map.of("id", "RHSA-2025:5784", "severity", "Moderate");
        Map<String, Object> errata8 = Map.of("id", "RHSA-2023:9999", "severity", "Important");
        Map<String, Object> errata9 = Map.of("id", "RHSA-2354:1478", "severity", "Moderate");
        Map<String, Object> errata10 = new HashMap<>();
        errata10.put("id", null);
        Map<String, Object> errata11 = new HashMap<>();
        errata11.put("id", "RHSA-8754:1315");
        errata11.put("severity", "RHSA-8754:1315");
        Map<String, Object> errata12 = Map.of("id", "RHSA-2824:0315");
        Map<String, Object> errata13 = Map.of("id", "RHSA-6587:1315", "severity", "Low");
        Map<String, Object> errata14 = Map.of("id", "RHSA-2025:1345", "severity", "Low");
        Map<String, Object> errata15 = Map.of("id", "RHSA-1287:1", "severity", "Moderate");

        List<Map<String, Object>> input = Arrays.asList(errata1, errata2, errata3, errata4, errata5, errata6, errata7, errata8, errata9, errata10, errata11, errata12, errata13, errata14, errata15);

        List<Map<String, Object>> result = ErrataSortExtension.sortErrataSecurityArray(input);

        // Important level
        assertEquals("RHSA-2124:0315", result.get(0).get("id"));
        assertEquals("Important", result.get(0).get("severity"));
        assertEquals("RHSA-2025:0315", result.get(1).get("id"));
        assertEquals("Important", result.get(1).get("severity"));
        assertEquals("RHSA-2023:9999", result.get(2).get("id"));
        assertEquals("Important", result.get(2).get("severity"));
        assertEquals("RHSA-1001:1315", result.get(3).get("id"));
        assertEquals("Important", result.get(3).get("severity"));

        // Moderate level
        assertEquals("RHSA-2354:1478", result.get(4).get("id"));
        assertEquals("Moderate", result.get(4).get("severity"));
        assertEquals("RHSA-2025:5784", result.get(5).get("id"));
        assertEquals("Moderate", result.get(5).get("severity"));
        assertEquals("RHSA-2023:001", result.get(6).get("id"));
        assertEquals("Moderate", result.get(6).get("severity"));
        assertEquals("RHSA-1287:1", result.get(7).get("id"));
        assertEquals("Moderate", result.get(7).get("severity"));

        // Low level
        assertEquals("RHSA-6587:1315", result.get(8).get("id"));
        assertEquals("Low", result.get(8).get("severity"));
        assertEquals("RHSA-2025:1345", result.get(9).get("id"));
        assertEquals("Low", result.get(9).get("severity"));
        assertEquals("RHSA-2022:1215", result.get(10).get("id"));
        assertEquals("Low", result.get(10).get("severity"));

        // Unrecognize level
        assertEquals("RHSA-8754:1315", result.get(11).get("id"));
        assertEquals("RHSA-8754:1315", result.get(11).get("severity"));
        assertEquals("RHSA-2824:0315", result.get(12).get("id"));
        assertNull(result.get(12).get("severity"));
        assertEquals("RHSA-2024:0315", result.get(13).get("id"));
        assertEquals("none", result.get(13).get("severity"));
        assertNull(result.get(14).get("severity"));
        assertNull(result.get(14).get("id"));
    }

    @Test
    void testSortById() {
        Map<String, Object> errata1 = Map.of("id", "RHSA-2024:0315");
        Map<String, Object> errata2 = Map.of("id", "RHSA-2025:1315");
        Map<String, Object> errata3 = Map.of("id", "RHSA-2025:0315");
        Map<String, Object> errata4 = Map.of("id", "RHSA-2023:9999");

        List<Map<String, Object>> input = Arrays.asList(errata1, errata2, errata3, errata4);

        List<Map<String, Object>> result = ErrataSortExtension.sortErrataArray(input);

        assertEquals("RHSA-2025:1315", result.get(0).get("id"));
        assertEquals("RHSA-2025:0315", result.get(1).get("id"));
        assertEquals("RHSA-2024:0315", result.get(2).get("id"));
        assertEquals("RHSA-2023:9999", result.get(3).get("id"));
    }

    @Test
    void testInvalidIdFormatAreIgnored() {
        Map<String, Object> valid = Map.of("id", "RHSA-2025:0001");
        Map<String, Object> invalid1 = Map.of("id", "bad-format");
        Map<String, Object> invalid2 = Map.of("id", "RHSA2025:9999");
        Map<String, Object> invalid3 = new HashMap<>();
        invalid3.put("id", null);

        List<Map<String, Object>> input = Arrays.asList(invalid1, valid, invalid2, invalid3);

        List<Map<String, Object>> result = ErrataSortExtension.sortErrataArray(input);

        // valid item should be first, others retain original position
        assertEquals("RHSA-2025:0001", result.get(0).get("id"));
        assertEquals("bad-format", result.get(1).get("id"));
        assertEquals("RHSA2025:9999", result.get(2).get("id"));
        assertNull(result.get(3).get("id"));
    }
}

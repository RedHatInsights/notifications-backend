package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateExtension;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrataSortExtension {

    // pattern to extract data from an errata ID such as "RHSA-2024:0315"
    private static final Pattern errataIdPattern = Pattern.compile("([A-Z]+)-([0-9]+):([0-9]+)");

    private static final Map<String, Integer> severityOrder = Map.of(
        "Important", 0,
        "Moderate", 1,
        "Low", 2
    );

    /* Sort errata security array by security level, then by id */
    @TemplateExtension
    public static List<Map<String, Object>> sortErrataSecurityArray(List<Map<String, Object>> list) {
        list.sort((m1, m2) -> {
            String sev1 = String.valueOf(m1.get("severity"));
            String sev2 = String.valueOf(m2.get("severity"));

            int order1 = severityOrder.getOrDefault(sev1, Integer.MAX_VALUE);
            int order2 = severityOrder.getOrDefault(sev2, Integer.MAX_VALUE);

            int cmp = Integer.compare(order1, order2);
            if (cmp != 0) {
                return cmp;
            }

            return sortById(m1, m2);
        });

        return list;
    }

    /* Sort errata array by id */
    @TemplateExtension
    public static List<Map<String, Object>> sortErrataArray(List<Map<String, Object>> list) {
        list.sort(ErrataSortExtension::sortById);
        return list;
    }

    private static int sortById(Map<String, Object> m1, Map<String, Object> m2) {
        Long id1 = extractIdIntValue(String.valueOf(m1.get("id")));
        Long id2 = extractIdIntValue(String.valueOf(m2.get("id")));
        return id2.compareTo(id1);
    }

    // Id format is like RHSA-2024:0315, we need to extract the numeric part to compare it
    private static Long extractIdIntValue(String id) {

        Matcher matcher = errataIdPattern.matcher(id);
        if (matcher.matches()) {
            String yearOnly = matcher.group(2);  // extracts "2024"
            String idOnly = matcher.group(3);    // extracts "0315"

            // we have to pad the second part of id to avoid 2025:0001 to be considered as lower than 2024:0010
            String paddedId = String.format("%1$9s", idOnly).replace(' ', '0');

            return Long.valueOf(yearOnly + paddedId);
        } else {
            Log.errorf("Unrecognized identifier format: %s", id);
            return 0L;
        }
    }
}


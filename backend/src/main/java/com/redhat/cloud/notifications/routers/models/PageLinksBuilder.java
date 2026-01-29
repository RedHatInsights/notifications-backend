package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.db.Query;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageLinksBuilder {

    public static Map<String, String> build(String apiPath, long count, Query query) {
        return build(apiPath, count, query.getLimit().getLimit(), query.getLimit().getOffset());
    }

    // New method that preserves query parameters
    public static Map<String, String> build(UriInfo uriInfo, long count, Query query) {
        return build(uriInfo, count, query.getLimit().getLimit(), query.getLimit().getOffset());
    }

    // New method that preserves query parameters
    public static Map<String, String> build(UriInfo uriInfo, long count, long limit, long currentOffset) {
        // Use UriBuilder to properly handle URL encoding of query parameters
        UriBuilder baseBuilder = UriBuilder.fromPath(uriInfo.getPath());

        // Add query parameters
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            for (String value : entry.getValue()) {
                baseBuilder.queryParam(entry.getKey(), value);
            }
        }

        baseBuilder.replaceQueryParam("limit", limit);
        return buildLinks(baseBuilder, count, limit, currentOffset);
    }

    public static Map<String, String> build(String apiPath, long count, long limit, long currentOffset) {
        UriBuilder baseBuilder = UriBuilder.fromPath(apiPath)
                .queryParam("limit", limit);
        return buildLinks(baseBuilder, count, limit, currentOffset);
    }

    private static Map<String, String> buildLinks(UriBuilder baseBuilder, long count, long limit, long currentOffset) {
        Map<String, String> links = new HashMap<>();

        // Calculate lastOffset first as it's needed for both 'last' and 'next' links
        long lastOffset;
        if (limit >= count) {
            lastOffset = 0;
        } else {
            long modulo = count % limit;
            if (modulo == 0) {
                modulo = limit;
            }
            lastOffset = count - (limit == 1 ? 1 : modulo);
        }

        // first
        links.put("first", baseBuilder.clone().replaceQueryParam("offset", 0).toTemplate());

        // last
        links.put("last", baseBuilder.clone().replaceQueryParam("offset", lastOffset).toTemplate());

        // prev
        if (currentOffset > 0) {
            long prevOffset = currentOffset - limit;
            if (prevOffset < 0) {
                prevOffset = 0;
            }
            links.put("prev", baseBuilder.clone().replaceQueryParam("offset", prevOffset).toTemplate());
        }

        // next
        if (currentOffset < lastOffset) {
            long nextOffset = currentOffset + limit;
            links.put("next", baseBuilder.clone().replaceQueryParam("offset", nextOffset).toTemplate());
        }

        return links;
    }
}

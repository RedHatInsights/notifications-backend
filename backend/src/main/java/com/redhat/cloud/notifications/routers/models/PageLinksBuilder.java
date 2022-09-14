package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.db.Query;

import java.util.HashMap;
import java.util.Map;

public class PageLinksBuilder {

    public static Map<String, String> build(String apiPath, long count, Query query) {
        return build(apiPath, count, query.getLimit().getLimit(), query.getLimit().getOffset());
    }

    public static Map<String, String> build(String apiPath, long count, long limit, long currentOffset) {
        Map<String, String> links = new HashMap<>();

        String baseLink = apiPath + "?limit=" + limit + "&offset=";

        // first
        links.put("first", baseLink + "0");

        // last
        long lastOffset;
        if (limit >= count) {
            lastOffset = 0;
        } else {
            lastOffset = count - (limit == 1 ? 1 : count % limit);
        }
        links.put("last", baseLink + lastOffset);

        // prev
        if (currentOffset > 0) {
            long prevOffset = currentOffset - limit;
            links.put("prev", baseLink + prevOffset);
        }

        // next
        if (currentOffset < lastOffset) {
            long nextOffset = currentOffset + limit;
            links.put("next", baseLink + nextOffset);
        }

        return links;
    }
}

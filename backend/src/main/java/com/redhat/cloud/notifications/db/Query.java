package com.redhat.cloud.notifications.db;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

public class Query {

    public static final int DEFAULT_RESULTS_PER_PAGE = 20;

    @QueryParam("limit")
    @DefaultValue(DEFAULT_RESULTS_PER_PAGE + "")
    @Min(value = 1, message = "The collection limit cannot be lower than {value}")
    @Max(value = 200, message = "The collection limit cannot be greater than {value}")
    Integer pageSize;

    @QueryParam("pageNumber")
    @Min(value = 1, message = "The page number cannot be lower than {value}")
    Integer pageNumber;

    @QueryParam("offset")
    @Min(value = 0, message = "The offset cannot be lower than {value}")
    Integer offset;

    @QueryParam("sort_by")
    public String sortBy;

    @QueryParam("sortBy")
    @Deprecated
    String sortByDeprecated;

    public static class Limit {
        private final int limit;
        private final int offset;

        public Limit(int limit, int offset) {
            this.limit = limit;
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getOffset() {
            return offset;
        }

        public static int calculateOffset(int pageNumber, int pageSize) {
            return pageNumber * pageSize;
        }
    }

    public Limit getLimit() {
        if (pageSize == null) {
            pageSize = DEFAULT_RESULTS_PER_PAGE;
        }

        // offset takes precedence over pageNumber if both are set
        if (pageNumber != null && offset == null) {
            offset = Limit.calculateOffset(pageNumber, pageSize);
        }
        if (offset == null) {
            offset = 0;
        }
        return new Limit(pageSize, offset);
    }

    @Override
    public String toString() {
        return "Query{" +
            "pageSize=" + pageSize +
            ", pageNumber=" + pageNumber +
            ", offset=" + offset +
            ", sortBy='" + sortBy + '\'' +
            '}';
    }
}

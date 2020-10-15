package com.redhat.cloud.notifications.db;

import javax.ws.rs.QueryParam;
import java.util.function.Function;

public class Query {
    @QueryParam("limit")
    private Integer pageSize;

    @QueryParam("pageNumber")
    private Integer pageNumber;

    @QueryParam("offset")
    private Integer offset;

    @QueryParam("sort_by")
    private String sortBy;

    public static class Limit {
        private int pageNumber;
        private int pageSize;
        private int offset;

        public Limit(int pageNumber, int pageSize) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int calculateOffset() {
            if (offset > 0) {
                return offset;
            }
            return pageNumber * pageSize;
        }
    }

    public Limit getLimit() {
        if (pageSize != null) {
            if (pageNumber == null) {
                pageNumber = 0;
            }
            return new Limit(pageNumber, pageSize);
        }
        return new Limit(0, 0);
    }

    public static class Sort {
        private String sortColumn;

        public enum Order {
            ASC, DESC
        }

        private Order sortOrder = Order.ASC;

        public Sort(String sortColumn) {
            this.sortColumn = sortColumn;
        }

        public String getSortColumn() {
            return sortColumn;
        }

        public void setSortColumn(String sortColumn) {
            this.sortColumn = sortColumn;
        }

        public Order getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Order sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    public Sort getSort() {
        if (sortBy == null || sortBy.length() < 1) {
            return null;
        }

        String[] sortSplit = sortBy.split(":");
        Sort sort = new Sort(sortSplit[0]);
        if (sortSplit.length > 1) {
            try {
                Sort.Order order = Sort.Order.valueOf(sortSplit[1].toUpperCase());
                sort.setSortOrder(order);
            } catch (IllegalArgumentException | NullPointerException iae) {
            }
        }
        return sort;
    }

    public static String getPostgresQuery(Limit limiter) {
        String builder = "LIMIT " +
                limiter.getPageSize() +
                " OFFSET " +
                limiter.calculateOffset();
        return builder;
    }

    public String getModifiedQuery(String basicQuery) {
        // Use the internal Query
        // What's the proper order? SORT first, then LIMIT? COUNT as last one?
        String query = basicQuery;
        Sort sort = getSort();
        if (sort != null) {
            query = modifyWithSort(query, sort);
        }
        Limit limiter = getLimit();
        if (limiter != null && (limiter.getPageSize() > 0 || limiter.calculateOffset() > 0)) {
            query = modifyQuery(query, limiter);
        }
        return query;
    }

    private static String modifyQuery(String basicQuery, Query.Limit limiter) {
        // TODO Take into account new limit+pageNumber keyword mess
        if (limiter != null && limiter.getPageSize() > 0) {
            String builder = basicQuery +
                    " " +
                    Query.getPostgresQuery(limiter);
            return builder;
        }

        return basicQuery;
    }

    public static Function<String, String> modifyToCountQuery() {
        return s -> {
            String builder = "SELECT COUNT(*) FROM (" +
                    s +
                    ") counted";
            return builder;
        };
    }

    public static String modifyToCountQuery(String theQuery) {
        String builder = "SELECT COUNT(*) FROM (" +
                theQuery +
                ") counted";
        return builder;
    }

    private static String modifyWithSort(String theQuery, Query.Sort sorter) {
        String builder = theQuery +
                "ORDER BY " +
                sorter.getSortColumn() +
                sorter.getSortOrder().toString();
        return builder;
    }
}

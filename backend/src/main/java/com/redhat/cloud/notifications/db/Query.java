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
        private int limit;
        private int offset;

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
        if (pageSize != null) {
            // offset takes precedence over pageNumber if both are set
            if (pageNumber != null && offset == null) {
                offset = Limit.calculateOffset(pageNumber, pageSize);
            }
            if (offset == null) {
                offset = 0;
            }
            return new Limit(pageSize, offset);
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
        // Endpoints: sort by: name, type, "last connection status" (?), enabled
        //      -> endpoint_id, name, endpoint_type, enabled are the accepted parameter names
        // TODO Should they be id, name, type, enabled for consistency and then modified in the actual query to Postgres?
        // And if it's not an accepted value? Throw exception?

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

    public String getModifiedQuery(String basicQuery) {
        // Use the internal Query
        // What's the proper order? SORT first, then LIMIT? COUNT as last one?
        String query = basicQuery;
        Sort sort = getSort();
        if (sort != null) {
            query = modifyWithSort(query, sort);
        }
        return query;
    }

    public String getSortQuery() {
        Sort sort = getSort();
        String query = "";
        if (sort != null) {
            query = modifyWithSort(query, sort);
        }
        return query;
    }

    public static Function<String, String> modifyToCountQuery() {
        return s -> "SELECT COUNT(*) FROM (" +
                s +
                ") counted";
    }

    public static String modifyToCountQuery(String theQuery) {
        return "SELECT COUNT(*) FROM (" +
                theQuery +
                ") counted";
    }

    private static String modifyWithSort(String theQuery, Query.Sort sorter) {
        return theQuery +
                " ORDER BY " +
                sorter.getSortColumn() + " " +
                sorter.getSortOrder().toString();
    }
}

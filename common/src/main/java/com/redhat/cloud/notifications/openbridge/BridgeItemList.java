package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.StringJoiner;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class BridgeItemList<T> {

    private String kind;
    private List<T> items;
    private int page;
    private int size;
    private int total;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BridgeItemList.class.getSimpleName() + "[", "]")
                .add("kind='" + kind + "'")
                .add("page=" + page)
                .add("size=" + size)
                .add("total=" + total)
                .toString();
    }
}

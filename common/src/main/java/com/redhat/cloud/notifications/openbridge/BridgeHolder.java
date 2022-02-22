package com.redhat.cloud.notifications.openbridge;

/**
 *
 */
public class BridgeHolder {

    private static BridgeHolder instance;
    private static Bridge bridge;

    private BridgeHolder() {
    }

    public static BridgeHolder getInstance() {
        if (instance == null) {
            instance = new BridgeHolder();
        }
        return instance;
    }

    public Bridge getBridge() {
        return bridge;
    }

    public void setBridge(Bridge bridge) {
        BridgeHolder.bridge = bridge;
    }
}

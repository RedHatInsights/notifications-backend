package com.redhat.cloud.notifications;

/**
 * Singleton to hold some stuff
 */
public class StuffHolder {

    private static StuffHolder stuffHolder;

    private boolean adminDown;
    private boolean degraded;

    public static StuffHolder getInstance() {
        if (stuffHolder == null) {
            stuffHolder = new StuffHolder();
        }
        return stuffHolder;
    }

    public boolean isAdminDown() {
        return adminDown;
    }

    public void setAdminDown(boolean adminDown) {
        this.adminDown = adminDown;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }
}

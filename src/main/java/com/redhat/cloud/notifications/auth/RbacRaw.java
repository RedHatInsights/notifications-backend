package com.redhat.cloud.notifications.auth;

import java.util.List;
import java.util.Map;

// TODO Use this from insights-common-java
public class RbacRaw {
    public Map<String, String> links;
    public Map<String, Integer> meta;
    public List<Map<String, Object>> data;

    public boolean canRead(String path) {
        return findPermission(path, "read");
    }

    public boolean canWrite(String path) {
        return findPermission(path, "write");
    }

    public boolean canReadAll() {
        return canRead("*");
    }

    public boolean canWriteAll() {
        return canWrite("*");
    }

    public boolean canDo(String path, String permission) {
        return findPermission(path, permission);
    }

    private boolean findPermission(String path, String what) {
        if (data == null || data.size() == 0) {
            return false;
        }

        for (Map<String, Object> permissionEntry : data) {
            String[] fields = getPermissionFields(permissionEntry);
            if (fields.length < 3) {
                return false;
            }
            if (fields[1].equals(path)) {
                if (fields[2].equals(what) || fields[2].equals("*")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String[] getPermissionFields(Map<String, Object> map) {
        String perms = (String) map.get("permission");
        return perms.split(":");
    }
}

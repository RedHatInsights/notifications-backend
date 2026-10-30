package com.redhat.cloud.notifications.qute.templates.extensions;

abstract class BaseExtension {

    protected static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

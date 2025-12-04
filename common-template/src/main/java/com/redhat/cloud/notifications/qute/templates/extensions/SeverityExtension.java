package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;

public class SeverityExtension {

    @TemplateExtension
    public static String severityAsEmailTitle(String severity) {
        if ("".equals(severity) || "UNDEFINED".equals(severity) || "NONE".equals(severity)) {
            return "";
        } else {
            return String.format("[%s] ", severity);
        }
    }

    @TemplateExtension
    public static String toTitleCase(String severity) {
        return switch (severity) {
            case "CRITICAL" -> "Critical";
            case "IMPORTANT" -> "Important";
            case "MODERATE" -> "Moderate";
            case "LOW" -> "Low";
            case "NONE" -> "None";
            case "UNDEFINED" -> "Undefined";
            default -> severity;
        };
    }

    @TemplateExtension
    public static String asSeverityEmoji(String severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "\u203C\uFE0F"; // Double Exclamation Mark, emoji style
            case "IMPORTANT" -> "\uD83D\uDFE0"; // Large Orange Circle
            case "MODERATE" -> "\uD83D\uDFE1"; // Large Yellow Circle
            case "LOW" -> "\u26AB"; // Medium Black Circle
            case "NONE" -> "\u2796"; // Heavy Minus Sign
            case "UNDEFINED" -> "\u2753"; // Red (or Black) Question Mark
            default -> "";
        };
    }

    @TemplateExtension
    public static String asPatternFlySeverity(String severity) {
        if (severity == null) {
            return null;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "critical";
            case "IMPORTANT" -> "important";
            case "MODERATE" -> "moderate";
            case "LOW" -> "minor";
            case "NONE" -> "none";
            case "UNDEFINED" -> "undefined";
            default -> null;
        };
    }
}

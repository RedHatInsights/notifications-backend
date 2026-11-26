package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Qute helper methods used by the RBAC (User Access) email and drawer templates
 * to construct links that are passed as parameters to i18n message bundles.
 *
 * <p>These are namespace-based extensions invoked as {@code rbacLink:methodName(args)}
 * in templates, so they do not depend on a specific base object type.</p>
 *
 * <p>All user-supplied values ({@code username}, {@code name}, {@code uuid}) are
 * treated as untrusted: display text is HTML-escaped or Markdown-escaped, and URL
 * path segments are percent-encoded, consistent with {@link IntegrationsExtension}.</p>
 */
@TemplateExtension(namespace = "rbacLink")
public class RbacExtension extends BaseExtension {

    /**
     * Builds an HTML {@code <a><b>username</b></a>} link to a user's detail page.
     */
    public static String userHtml(String username, String style, String baseUrl) {
        String encodedPath = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String safeDisplay = escapeHtml(username);
        return "<a style=\"" + style + "\" href=\"" + baseUrl
            + "/iam/user-access/users/detail/" + encodedPath + "\"><b>" + safeDisplay + "</b></a>";
    }

    /**
     * Builds an HTML {@code <a><b>groupName</b></a>} link to a group's detail page.
     */
    public static String groupHtml(String name, String uuid, String style, String baseUrl) {
        String encodedPath = URLEncoder.encode(uuid, StandardCharsets.UTF_8);
        String safeDisplay = escapeHtml(name);
        return "<a style=\"" + style + "\" href=\"" + baseUrl
            + "/iam/user-access/groups/detail/" + encodedPath + "\"><b>" + safeDisplay + "</b></a>";
    }

    /**
     * Builds a Markdown bold link to a user's detail page for drawer notifications.
     */
    public static String userMd(String username, String baseUrl, String queryParams) {
        String encodedPath = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String safeDisplay = escapeMd(username);
        return "**[" + safeDisplay + "](" + baseUrl
            + "/iam/user-access/users/detail/" + encodedPath + "?" + queryParams + ")**";
    }

    /**
     * Builds a Markdown bold link to a group's detail page for drawer notifications.
     */
    public static String groupMd(String name, String uuid, String baseUrl, String queryParams) {
        String encodedPath = URLEncoder.encode(uuid, StandardCharsets.UTF_8);
        String safeDisplay = escapeMd(name);
        return "**[" + safeDisplay + "](" + baseUrl
            + "/iam/user-access/groups/detail/" + encodedPath + "?" + queryParams + ")**";
    }

    /**
     * Escapes Markdown link-breaking characters in display text to prevent
     * link injection via attacker-controlled usernames or group names.
     */
    private static String escapeMd(String value) {
        return value.replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("`", "\\`")
            .replace("\n", " ")
            .replace("\r", " ");
    }
}

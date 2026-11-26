package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;

/**
 * Qute helper methods used by the RBAC (User Access) email and drawer templates
 * to construct links that are passed as parameters to i18n message bundles.
 *
 * <p>These are namespace-based extensions invoked as {@code rbacLink:methodName(args)}
 * in templates, so they do not depend on a specific base object type.</p>
 */
@TemplateExtension(namespace = "rbacLink")
public class RbacExtension extends BaseExtension {

    /**
     * Builds an HTML {@code <a><b>username</b></a>} link to a user's detail page.
     */
    public static String userHtml(String username, String style, String baseUrl) {
        return "<a style=\"" + style + "\" href=\"" + baseUrl
            + "/iam/user-access/users/detail/" + username + "\"><b>" + username + "</b></a>";
    }

    /**
     * Builds an HTML {@code <a><b>groupName</b></a>} link to a group's detail page.
     */
    public static String groupHtml(String name, String uuid, String style, String baseUrl) {
        return "<a style=\"" + style + "\" href=\"" + baseUrl
            + "/iam/user-access/groups/detail/" + uuid + "\"><b>" + name + "</b></a>";
    }

    /**
     * Builds a Markdown bold link to a user's detail page for drawer notifications.
     */
    public static String userMd(String username, String baseUrl, String queryParams) {
        return "**[" + username + "](" + baseUrl
            + "/iam/user-access/users/detail/" + username + "?" + queryParams + ")**";
    }

    /**
     * Builds a Markdown bold link to a group's detail page for drawer notifications.
     */
    public static String groupMd(String name, String uuid, String baseUrl, String queryParams) {
        return "**[" + name + "](" + baseUrl
            + "/iam/user-access/groups/detail/" + uuid + "?" + queryParams + ")**";
    }
}

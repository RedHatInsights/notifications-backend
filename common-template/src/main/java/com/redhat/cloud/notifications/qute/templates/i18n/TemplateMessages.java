package com.redhat.cloud.notifications.qute.templates.i18n;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle
public interface TemplateMessages {

    @Message("Integrations - Console")
    String integrationsConsoleTitle();

    @Message("Integration disabled")
    String integrationDisabledTitle();

    @Message("Integration <a style=\"{style}\" href=\"{href}\" target=\"_blank\"><b>{name}</b></a> was disabled because the remote endpoint responded with an HTTP status code {statusCode}.")
    String integrationDisabledHttp4xx(String style, String href, String name, Object statusCode);

    @Message("Integration <a style=\"{style}\" href=\"{href}\" target=\"_blank\"><b>{name}</b></a> was disabled because the connection couldn't be established with the remote endpoint, or it responded too many times with a server error (HTTP status code 5xx) after {errorsCount} attempts.")
    String integrationDisabledOther(String style, String href, String name, Object errorsCount);

    @Message("The latest error was: {errorDetails}.")
    String integrationDisabledLatestError(Object errorDetails);

    @Message("The latest error was: {errorDetails} {statusCode}.")
    String integrationDisabledLatestErrorHttp5xx(Object errorDetails, Object statusCode);

    @Message("Please review the integration configuration from <a style=\"{style}\" href=\"{href}\" target=\"_blank\">Settings > Integrations</a> to troubleshoot and re-enable the integration.")
    String integrationDisabledReviewPrompt(String style, String href);

    @Message("Integrations")
    String integrationsButtonLabel();
}

package com.redhat.cloud.notifications.qute.templates.i18n;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle("rbac")
public interface RbacMessages {

    @Message("User Access - Console")
    String userAccessConsoleTitle();

    @Message("Group created")
    String groupCreatedTitle();

    @Message("{userLink} created the {groupLink} group.")
    String groupCreatedBody(String userLink, String groupLink);

    @Message("User Access")
    String userAccessButtonLabel();
}

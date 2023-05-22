package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.InternalCamelTemporaryService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.Path;

@RegisterRestClient(configKey = "internal-google-chat")
@Path(GoogleChatRouteBuilder.REST_PATH)
public interface InternalTemporaryGoogleChatService extends InternalCamelTemporaryService {

}

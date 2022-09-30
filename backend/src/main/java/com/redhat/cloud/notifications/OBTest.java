package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.BridgeEventService;
import com.redhat.cloud.notifications.openbridge.Processor;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper during development
 */
@Path("/internal/ob-test")
public class OBTest {

    public static final String SLACK_ACTION = "slack_sink_0.1";
    public static final String SLACK_CHANNEL = "slack_channel";
    public static final String SLACK_WEBHOOK_URL = "slack_webhook_url";

    @Inject
    @RestClient
    BridgeApiService apiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    @Inject
    TemplateRepository templateRepository;

    @GET
    public String getOurBridge() {

        return bridge.toString();
    }

    @GET
    @Path("/bs")
    public String getBridges() {
        String token = bridgeAuth.getToken();
        Map<String, Object> bridges = apiService.getBridges(token);

        return bridges.toString();
    }


    @GET
    @Path("/ps")
    public String getProcessorsForBridge() {

        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        if (!bridge.getStatus().equals("ready")) {
            return "Bridge is not yet ready, but " + bridge.getStatus();
        }

        Map<String, Object> processors = apiService.getProcessors(bridgeId, "", token);

        return processors.toString();
    }


    /*
    Format of a processor:
    {
      "name": "string",
      "filters": [
        {
          "type": "string",
          "key": "string"
        }
      ],
      "transformationTemplate": "string",
      "action": {
        "name": "string",
        "type": "string",
        "parameters": {
          "additionalProp1": "string"
        }
      }
    }
     */
    @POST
    @Path("/ps/{name}")
    public String addProcessor(@PathParam("name") String processorName) {

        Processor pro = new Processor(processorName);
        Processor.Action action = new Processor.Action(SLACK_ACTION);

        action.getParameters().put(SLACK_CHANNEL, "#mychannel");
        action.getParameters().put(SLACK_WEBHOOK_URL, "https://hooks.slack.com/services/blabla/blabla/blabla");

        pro.action = action;

        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        Processor result = apiService.addProcessor(bridgeId, token, pro);
        System.out.println(result);

        return "submitted " + result.getId();
    }

    @PUT
    @Path("/ps/{id}")
    public String updateProcessor(@PathParam("id") String pid) {

        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        Processor p = apiService.getProcessorById(bridgeId, pid, token);

        Optional<IntegrationTemplate> template = templateRepository.findIntegrationTemplate(null, null,
                IntegrationTemplate.TemplateKind.APPLICATION, "slack");
        if (template.isPresent()) {
            p.setTransformationTemplate(template.get().getTheTemplate().getData());
        } else {
            p.setTransformationTemplate("A template after update");
        }
        Processor result = apiService.updateProcessor(bridgeId, pid, token, p);

        return result.toString();
    }

    @GET
    @Path("/ps/{id}")
    public Processor getProcessorById(@PathParam("id") String processorId) {
        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        Processor processorById = apiService.getProcessorById(bridgeId, processorId, token);
        return processorById;

    }

    @DELETE
    @Path("/ps/{id}")
    public String deleteProcessor(@PathParam("id") String processorId) {

        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        apiService.deleteProcessor(bridgeId, processorId, token);

        return "submitted";
    }

    @DELETE
     @Path("/psd/{name}")
    public String deleteProcessorsByNamePrefix(@PathParam("name") String prefix) {
        String bridgeId = bridge.getId();
        String token = bridgeAuth.getToken();

        StringBuilder deleted = new StringBuilder();
        Map<String, Object> processors = apiService.getProcessors(bridgeId, prefix, token);
        List<Map<String, Object>> items = (List<Map<String, Object>>) processors.get("items");
        for (Map<String, Object> p : items) {
            String id = (String) p.get("id");
            String status = (String) p.get("status");
            if (!"ready".equals(status)) {
                System.out.println("Skip processor " + id + " with status " + status);
                continue; // We can only delete those that are ready
            }
            try {
                apiService.deleteProcessor(bridgeId, id, token);
                System.out.println("Deleted " + id + " -- " + p.get("name"));
                deleted.append(id);
                deleted.append("\n");
            } catch (WebApplicationException wae) {
                System.err.println("Deletion failed for processor " + id + ": " + wae);
            }
        }
        return deleted.toString();
    }

    @POST
    @Path("/e/{accountId}/{pn}")
    public String sendEvent(@PathParam("accountId") String accountId, @PathParam("pn") String processor) {

        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("test", "lala");

        events.add(payload);
        data.put("events", events);

        return sendEventInternal(data, accountId, processor);

    }

    private String sendEventInternal(Map<String, Object> data, String accountId, String processor) {

        Map<String, Object> ce = new HashMap<>();
        String uuid = UUID.randomUUID().toString();
        ce.put("id", uuid);
        ce.put("source", "notifications");
        ce.put("specversion", "1.0");
        ce.put("type", "myType");
        ce.put("rhaccount", accountId);
        ce.put("processorname", processor);

        // TODO add dataschema

        ce.put("data", data);

        String token = bridgeAuth.getToken();
        String endpoint = bridge.getEndpoint();

        BridgeEventService evtSvc = RestClientBuilder.newBuilder()
                .baseUri(URI.create(endpoint))
                .build(BridgeEventService.class);

        JsonObject jsonObject = JsonObject.mapFrom(ce);
        evtSvc.sendEvent(jsonObject, token);

        return uuid;
    }

}




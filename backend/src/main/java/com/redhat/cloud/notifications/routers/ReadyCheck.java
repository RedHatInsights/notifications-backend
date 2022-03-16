package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task to check (OpenBridge) Endpoints
 * for their state. Those endpoints can go from
 * 'accepted' to 'provisioning' and then 'ready' or 'failed'.
 * We need to poll OB regularly to see when they are
 * good and then set the state accordingly.
 */
@ApplicationScoped
public class ReadyCheck  {

    private static final Logger LOGGER = Logger.getLogger(ReadyCheck.class);

    @Inject
    EntityManager em;

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    String endpointQueryString = "SELECT e FROM Endpoint e " +
            "WHERE e.compositeType.type = :type AND e.compositeType.subType IN (:subTypes) " +
            "AND e.status NOT IN (:ready, :failed) ";

    @Scheduled(concurrentExecution = Scheduled.ConcurrentExecution.SKIP, every = "10s")
    @Transactional
    public void execute() {

        Query query = em.createQuery(endpointQueryString);
        query.setParameter("ready", EndpointStatus.READY);
        query.setParameter("failed", EndpointStatus.FAILED);
        query.setParameter("type", EndpointType.CAMEL);
        query.setParameter("subTypes", "slack");
        List<Endpoint> endpoints = query.getResultList();
        for (Endpoint ep : endpoints) {

            if (!ep.getType().equals(EndpointType.CAMEL)) {
                LOGGER.warn("Not a camel endpoint, ignoring " + ep);
            } else {
                CamelProperties cp = em.find(CamelProperties.class, ep.getId()); // TODO Fetch in one go
                String processorId = cp.getExtras().get("processorId");
                try {
                    Map<String, Object> processor = bridgeApiService.getProcessorById(bridge.getId(), processorId, bridgeAuth.getToken());
                    String status = (String) processor.get("status");
                    LOGGER.debugf("  Status reported by OB for processor %s : %s", processorId, status);
                    if (status.equals("ready")) {
                        ep.setStatus(EndpointStatus.READY);
                    }
                    if (status.equals("failed")) {
                        ep.setStatus(EndpointStatus.FAILED);
                    }
                } catch (WebApplicationException wae) {
                    LOGGER.warn("Getting data from OB failed: " + wae.getMessage());
                    ep.setStatus(EndpointStatus.FAILED);
                }
            }
        }
    }
}

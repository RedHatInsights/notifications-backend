package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.BridgeItemList;
import com.redhat.cloud.notifications.openbridge.ProcessingError;
import com.redhat.cloud.notifications.openbridge.RhoseErrorMetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.WebApplicationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.redhat.cloud.notifications.openbridge.BridgeApiService.BASE_PATH;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 *
 */
@ApplicationScoped
public class FromOpenBridgeHistoryFiller {

    public static final String RHOSE_ORIGINAL_EVENT_ID_HEADER = "rhose-original-event-id";
    public static final String MESSAGES_WITH_ERROR_NAME = "rhose.messages.error";
    public static final String DEAD_LETTER_CAUSE = "dead-letter-cause";
    public static final String DEAD_LETTER_REASON = "dead-letter-reason";

    private static final Object DUMMY_CACHE_VALUE = new Object();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    RhoseErrorMetricsRecorder rhoseErrorMetricsRecorder;

    @Inject
    CamelHistoryFillerHelper camelHistoryFillerHelper;

    private Counter messagesWithError;

    @CacheName("from-open-bridge-history-filler")
    Cache seenIds;

    @PostConstruct
    void init() {
        messagesWithError = meterRegistry.counter(MESSAGES_WITH_ERROR_NAME);
    }

    @Scheduled(concurrentExecution = SKIP, every = "${ob.error-check.period:10s}")
    @Transactional
    public void execute() {

        if (!featureFlipper.isObBackchannelFillerEnabled()) {
            return;
        }

        BridgeItemList<ProcessingError> errorList;
        try {
            errorList = bridgeApiService.getProcessingErrors(bridge.getId(), bridgeAuth.getToken());
        } catch (WebApplicationException e) {
            String path = "GET " + BASE_PATH + "/{bridgeId}/errors";
            rhoseErrorMetricsRecorder.record(path, e);
            throw e;
        }

        // OB offers us a list of items that represent the last X errors
        // We may have seen individual items before
        List<ProcessingError> items = errorList.getItems();
        if (items == null) {
            return;
        }

        for (ProcessingError pe : items) {

            // We may have seen an item before, skip it
            String historyId = pe.getHeaders().get(RHOSE_ORIGINAL_EVENT_ID_HEADER);
            if (alreadyProcessed(historyId)) {
                continue;
            }

            statelessSessionFactory.withSession(statelessSession -> {
                Map<String, Object> decodedPayload = decodeItem(pe);
                // TODO reinjectIf needed (?)
                try {
                    camelHistoryFillerHelper.updateHistoryItem(decodedPayload);
                    messagesWithError.increment();
                } catch (Exception e) {
                    Log.warn("|  History update failed", e);
                }
            });
        }
    }

    private Map<String, Object> decodeItem(ProcessingError pe) {
        Map<String, Object> map = new HashMap<>();
        Map<String, String> headers = pe.getHeaders();
        map.put("historyId", headers.get(RHOSE_ORIGINAL_EVENT_ID_HEADER));
        map.put("successful", false);
        Map<String, String> details = new HashMap<>();
        details.put("originalEvent", pe.getPayload().toString());
        if (headers.containsKey(DEAD_LETTER_CAUSE)) {
            details.put(DEAD_LETTER_CAUSE, headers.get(DEAD_LETTER_CAUSE));
        }
        if (headers.containsKey(DEAD_LETTER_REASON)) {
            details.put(DEAD_LETTER_REASON, headers.get(DEAD_LETTER_REASON));
        }
        map.put("details", details);
        return map;
    }

    boolean alreadyProcessed(String historyId) {
        AtomicBoolean seen = new AtomicBoolean(true);
        seenIds.get(historyId, k -> {
            // If the value loader function is invoked, then we've never seen the current historyId.
            seen.set(false);
            return DUMMY_CACHE_VALUE;
        }).await().indefinitely();
        return seen.get();
    }
}

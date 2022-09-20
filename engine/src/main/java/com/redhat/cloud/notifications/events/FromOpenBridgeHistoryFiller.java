package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.BridgeItemList;
import com.redhat.cloud.notifications.openbridge.ProcessingError;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 *
 */
@ApplicationScoped
public class FromOpenBridgeHistoryFiller {

    public static final String ORIGINAL_EVENT_ID_HEADER = "rhose-original-event-id";
    public static final String MESSAGES_WITH_ERROR_NAME = "rhose.messages.error";
    public static final String DEAD_LETTER_CAUSE = "dead-letter-cause";

    @ConfigProperty(name = "ob.backchannel-filler.enabled", defaultValue = "true")
    boolean pollEnabled;

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    MeterRegistry meterRegistry;

    private Counter messagesWithError;

    private List<String> seenIds; // TODO should be some kind of real cache with expiry

    @PostConstruct
    void init() {
        messagesWithError = meterRegistry.counter(MESSAGES_WITH_ERROR_NAME);
        seenIds = new ArrayList<>();
    }

    @Scheduled(concurrentExecution = SKIP, every = "${ob.error-check.period:10s}")
    @Transactional
    public void execute() {

        if (!pollEnabled) {
            return;
        }

        BridgeItemList<ProcessingError> errorList = bridgeApiService.getProcessingErrors(bridge.getId(), bridgeAuth.getToken());

        // OB offers us a list of items that represent the last X errors
        // We may have seen individual items before
        List<ProcessingError> items = errorList.getItems();
        if (items == null) {
            return;
        }

        for (ProcessingError pe : items) {

            // We may have seen an item before, skip it
//            String historyId = pe.getHeaders().get(ORIGINAL_EVENT_ID_HEADER);
//            if (alreadyProcessed(historyId)) {
//                continue;
//            }

            statelessSessionFactory.withSession(statelessSession -> {
                Map<String, Object> decodedPayload = decodeItem(pe);
                // TODO reinjectIf needed (?)
                try {
                    notificationHistoryRepository.updateHistoryItem(decodedPayload);
                    messagesWithError.increment();
                } catch (Exception e) {
                    Log.info("|  Update Fail", e);
                }
            });
        }
    }

    private Map<String, Object> decodeItem(ProcessingError pe) {
        Map<String, Object> map = new HashMap<>();
        Map<String, String> headers = pe.getHeaders();
        map.put("historyId", headers.get(ORIGINAL_EVENT_ID_HEADER));
        map.put("successful", false);
        Map<String, String> details = new HashMap<>();
        details.put("originalEvent", pe.getPayload().toString());
        if (headers.containsKey(DEAD_LETTER_CAUSE)) {
            String cause = headers.get(DEAD_LETTER_CAUSE);
            details.put("cause", cause);
        }
        map.put("details", details);
        return map;
    }


    boolean alreadyProcessed(String id) {
        if (seenIds.contains(id)) {
            return true;
        }

        seenIds.add(id);
        return false;
    }
}

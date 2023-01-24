package com.redhat.cloud.notifications.routers.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import io.quarkus.logging.Log;
import org.jboss.resteasy.reactive.RestPath;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import java.time.Duration;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

/*
 * /!\ WARNING /!\
 * USE THIS REST RESOURCE WITH EXTREME CAUTION
 * /!\ WARNING /!\
 */
// TODO Remove this class ASAP!
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/behavior-group-cleanup")
public class BehaviorGroupCleanupResource {

    private static final Object DUMMY_CACHE_VALUE = new Object();

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1L))
            .build();

    @Inject
    EntityManager entityManager;

    @PUT
    @Path("/{orgId}/initiate")
    public void initiate(@RestPath String orgId) {
        Log.warnf("If you are not sure of what you are doing, STOP NOW! " +
                "Behavior groups cleanup is enabled for 1 minute for orgId=%s. " +
                "Nothing has been deleted yet from the DB. " +
                "Call the confirmation endpoint to delete all behavior groups from orgId=%s.", orgId, orgId);
        cache.put(orgId, DUMMY_CACHE_VALUE);
    }

    @PUT
    @Path("/{orgId}/confirm")
    @Transactional
    public Response confirm(@RestPath String orgId) {
        if (cache.getIfPresent(orgId) == null) {
            Log.warnf("Confirmation endpoint of behavior group cleanup was called without prior initialization for orgId=%s", orgId);
            return Response.status(400).build();
        } else {
            Log.warnf("Behavior groups cleanup has been confirmed for orgId=%s", orgId);
            cache.invalidate(orgId);
            long count = entityManager.createQuery("DELETE FROM BehaviorGroup WHERE orgId = :orgId")
                    .setParameter("orgId", orgId)
                    .executeUpdate();
            Log.warnf("%d behavior groups from orgId=%s were deleted from the DB", orgId, count);
            return Response.ok().entity(count).build();
        }
    }
}

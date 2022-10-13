package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.Constants;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

/**
 * This class will grab the incoming "x-rh-identity" header and forward it along when sending requests to Sources. We
 * could have used the default header factory and then the "propagateHeaders" property, but in order to avoid any
 * issues with future header factories that might want to do that and not forward the "x-rh-identity" header, this
 * simple class is built instead.
 */
public class SourcesXRHIDHeaderFactory implements ClientHeadersFactory {

    /**
     * Grabs the incoming "x-rh-identity" header and returns it.
     * @param incomingHeaders the incoming headers. They are left untouched.
     * @param outgoingHeaders the outgoing headers. They are left untouched.
     * @return a map containing the "x-rh-identity" header.
     */
    @Override
    public MultivaluedMap<String, String> update(final MultivaluedMap<String, String> incomingHeaders, final MultivaluedMap<String, String> outgoingHeaders) {
        final var headers = new MultivaluedHashMap<String, String>();
        headers.add(Constants.X_RH_IDENTITY_HEADER, incomingHeaders.getFirst(Constants.X_RH_IDENTITY_HEADER));

        return headers;
    }
}

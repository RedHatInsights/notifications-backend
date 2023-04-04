package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.Endpoint;

import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * A checked exception which is thrown from the {@link SecretUtils} class,
 * to force callers to implement measurements whenever an unsuccessful response
 * is received from Sources.
 */
public class SourcesException extends Exception {
    /**
     * The method that caused the exception.
     */
    private final Method method;

    /**
     * The optional endpoint UUID that might get appended to the exception's
     * message.
     */
    private final Optional<UUID> endpointUuid;

    /**
     * Used to determine if the exception was thrown during an update
     * operation. In such case, the exception's message will indicate that.
     */
    private final boolean isUpdateOperation;

    /**
     * The received response from Sources.
     */
    private final Response response;

    /**
     * The optional secret ID the that might get appended to the exception's
     * message.
     */
    private final Optional<Long> secretId;

    /**
     * A basic constructor for when there is no endpoint UUID and secret
     * UUIDs available. Used in {@link SecretUtils#createSecretsForEndpoint(Endpoint)}.
     * @param e the {@link SourcesRuntimeException} that was thrown by the
     *          {@link SourcesService}.
     */
    public SourcesException(final SourcesRuntimeException e) {
        this.endpointUuid = Optional.empty();
        this.isUpdateOperation = false;
        this.method = e.getMethod();
        this.response = e.getResponse();
        this.secretId = Optional.empty();
    }

    /**
     * A constructor for when the endpoint UUID is available.
     *
     * @param endpointUuid      the endpoint's {@link UUID} which will get
     *                          appended to the exception's message.
     * @param isUpdateOperation has the exception been thrown in an update
     *                          operation? If so, this will be indicated in the
     *                          exception's message.
     * @param e                 the {@link SourcesRuntimeException} that was
     *                          thrown by the {@link SourcesService}.
     */
    public SourcesException(final UUID endpointUuid, final boolean isUpdateOperation, final SourcesRuntimeException e) {
        this.endpointUuid = Optional.of(endpointUuid);
        this.isUpdateOperation = isUpdateOperation;
        this.method = e.getMethod();
        this.response = e.getResponse();
        this.secretId = Optional.empty();
    }

    /**
     * A constructor for when the endpoint's UUID and the secret's ID are
     * available.
     * @param endpointUuid      the endpoint's {@link UUID} which will get
     *                          appended to the exception's message.
     * @param secretId          the secret's ID which will get appended to the
     *                          exception's message.
     * @param isUpdateOperation has the exception been thrown in an update
     *                          operation? If so, this will be indicated in the
     *                          exception's message.
     * @param e                 the {@link SourcesRuntimeException} that was
     *                          thrown by the {@link SourcesService}.
     */
    public SourcesException(final UUID endpointUuid, final long secretId, final boolean isUpdateOperation, final SourcesRuntimeException e) {
        this.endpointUuid = Optional.of(endpointUuid);
        this.isUpdateOperation = isUpdateOperation;
        this.method = e.getMethod();
        this.response = e.getResponse();
        this.secretId = Optional.of(secretId);
    }

    /**
     * Returns the detail message string of this throwable.
     *
     * @return  the detail message string of this {@code Throwable} instance.
     */
    @Override
    public String getMessage() {
        final StringBuilder errorMessage = new StringBuilder();

        this.endpointUuid.ifPresent(uuid -> errorMessage.append(String.format("[endpoint_uuid: %s]", uuid)));
        this.secretId.ifPresent(secretId -> errorMessage.append(String.format("[secret_id: %s]", secretId)));

        errorMessage.append(String.format("[method: %s]", this.method.getName()));
        errorMessage.append(String.format("[response_status_code: %s]", this.response.getStatus()));

        if (this.isUpdateOperation) {
            errorMessage.append(" Sources returned an unexpected response during an endpoint update operation: ");
        } else {
            errorMessage.append(" Sources returned an unexpected response: ");
        }

        errorMessage.append(this.response.readEntity(String.class));

        return errorMessage.toString();
    }
}

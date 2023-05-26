package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.ExportRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ExportRequestClass;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.parser.GenericConsoleCloudEvent;
import com.redhat.cloud.notifications.exports.filters.events.EventFiltersExtractor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class ExportEventTestHelper {

    public static final LocalDateTime EXPORT_CE_TIME = LocalDateTime.now(ZoneOffset.UTC);
    public static final String EXPORT_CE_DATA_SCHEMA = "https://console.redhat.com/api/schemas/apps/export-service/v1/export-request.json";
    public static final String EXPORT_CE_SOURCE = ExportEventListener.EXPORT_SERVICE_URN;
    public static final String EXPORT_CE_SPEC_VERSION = "1.0";
    public static final String EXPORT_CE_TYPE = ExportEventListener.CE_EXPORT_REQUEST_TYPE;
    /**
     * The {@link UUID} of the export request which is the parent of the
     * resource requests. One export request will be composed of many resource
     * requests sent to different applications.
     */
    public static final UUID EXPORT_CE_EXPORT_UUID = UUID.randomUUID();
    public static final String EXPORT_CE_SUBJECT = String.format("urn:redhat:subject:export-service:request:%s", EXPORT_CE_EXPORT_UUID);
    /**
     * The {@link UUID} of the message itself.
     */
    public static final UUID EXPORT_CE_ID = UUID.randomUUID();
    /**
     * The {@link UUID} of the requested resource.
     */
    public static final UUID EXPORT_CE_RESOURCE_UUID = UUID.randomUUID();

    /**
     * <p>A base64 encoded XRHID header containing:
     * <ul>
     *     <li>The {@link com.redhat.cloud.notifications.TestConstants#DEFAULT_ACCOUNT_ID}.</li>
     *     <li>The {@link com.redhat.cloud.notifications.TestConstants#DEFAULT_ORG_ID}.</li>
     *     <li>A user with the following attributes:
     *         <ul>
     *             <li>Username: test_user</li>
     *             <li>Email: tuser@redhat.com</li>
     *             <li>First name: test</li>
     *             <li>Last name: user</li>
     *             <li>Is active?: true</li>
     *             <li>Is org admin?: false</li>
     *             <li>Is internal?: false</li>
     *         </ul>
     *     </li>
     * </ul>
     * </p>
     */
    public static final String EXPORT_CE_XHRID = "ewogICAgImlkZW50aXR5IjogewogICAgICAgICJhY2NvdW50X251bWJlciI6ICJkZWZhdWx0LWFjY291bnQtaWQiLAogICAgICAgICJvcmdfaWQiOiAiZGVmYXVsdC1vcmctaWQiLAogICAgICAgICJpbnRlcm5hbCI6IHsKICAgICAgICAgICAgIm9yZ19pZCI6ICIiCiAgICAgICAgfSwKICAgICAgICAidXNlciI6IHsKICAgICAgICAgICAgInVzZXJuYW1lIjogInRlc3RfdXNlciIsCiAgICAgICAgICAgICJlbWFpbCI6ICJ0dXNlckByZWRoYXQuY29tIiwKICAgICAgICAgICAgImZpcnN0X25hbWUiOiAidGVzdCIsCiAgICAgICAgICAgICJsYXN0X25hbWUiOiAidXNlciIsCiAgICAgICAgICAgICJpc19hY3RpdmUiOiB0cnVlLAogICAgICAgICAgICAiaXNfb3JnX2FkbWluIjogZmFsc2UsCiAgICAgICAgICAgICJpc19pbnRlcm5hbCI6IGZhbHNlLAogICAgICAgICAgICAibG9jYWxlIjogIiIsCiAgICAgICAgICAgICJ1c2VyX2lkIjogIiIKICAgICAgICB9LAogICAgICAgICJzeXN0ZW0iOiB7fSwKICAgICAgICAiYXNzb2NpYXRlIjogewogICAgICAgICAgICAiUm9sZSI6IG51bGwsCiAgICAgICAgICAgICJlbWFpbCI6ICIiLAogICAgICAgICAgICAiZ2l2ZW5OYW1lIjogIiIsCiAgICAgICAgICAgICJyaGF0VVVJRCI6ICIiLAogICAgICAgICAgICAic3VybmFtZSI6ICIiCiAgICAgICAgfSwKICAgICAgICAieDUwOSI6IHsKICAgICAgICAgICAgInN1YmplY3RfZG4iOiAiIiwKICAgICAgICAgICAgImlzc3Vlcl9kbiI6ICIiCiAgICAgICAgfSwKICAgICAgICAidHlwZSI6ICJVc2VyIiwKICAgICAgICAiYXV0aF90eXBlIjogIiIKICAgIH0KfQ==";

    /**
     * Generates an "export request" Cloud Event fixture with the provided
     * format.
     * @param format the format the "export request" is going to request the
     *               payload contents in.
     * @return the generated "export request" Cloud Event.
     */
    public static GenericConsoleCloudEvent<ExportRequest> createExportCloudEventFixture(final Format format) {
        final LocalDate today = LocalDate.now();

        final Map<String, Object> filters = new HashMap<>();
        filters.put(EventFiltersExtractor.FILTER_DATE_FROM, today.minusDays(10).toString());
        filters.put(EventFiltersExtractor.FILTER_DATE_TO, today.minusDays(5).toString());

        final ExportRequestClass exportRequestClass = new ExportRequestClass();
        exportRequestClass.setApplication(ExportEventListener.APPLICATION_NAME);
        exportRequestClass.setFilters(filters);
        exportRequestClass.setFormat(format);
        exportRequestClass.setResource(ExportEventListener.RESOURCE_TYPE_EVENTS);
        exportRequestClass.setUUID(EXPORT_CE_RESOURCE_UUID);
        exportRequestClass.setXRhIdentity(EXPORT_CE_XHRID);

        final ExportRequest exportRequest = new ExportRequest();
        exportRequest.setExportRequest(exportRequestClass);

        final GenericConsoleCloudEvent<ExportRequest> cce = new GenericConsoleCloudEvent<>();

        cce.setAccountId(DEFAULT_ACCOUNT_ID);
        cce.setData(exportRequest);
        cce.setDataSchema(EXPORT_CE_DATA_SCHEMA);
        cce.setId(EXPORT_CE_ID);
        cce.setOrgId(DEFAULT_ORG_ID);
        cce.setSource(EXPORT_CE_SOURCE);
        cce.setSpecVersion(EXPORT_CE_SPEC_VERSION);
        cce.setSubject(EXPORT_CE_SUBJECT);
        cce.setTime(EXPORT_CE_TIME);
        cce.setType(EXPORT_CE_TYPE);

        return cce;
    }
}

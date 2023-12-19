/* eslint-disable */
/**
 * Generated code, DO NOT modify directly.
 */
import * as z from 'zod';
import { ValidatedResponse } from 'openapi2typescript';
import { Action } from 'react-fetching-library';
import { ValidateRule } from 'openapi2typescript';
import {
    actionBuilder,
    ActionValidatableConfig,
} from 'openapi2typescript/react-fetching-library';

export namespace Schemas {
  export const AddAccessRequest = zodSchemaAddAccessRequest();
  export type AddAccessRequest = {
    application_id?: UUID | undefined | null;
    role?: string | undefined | null;
  };

  export const AddApplicationRequest = zodSchemaAddApplicationRequest();
  export type AddApplicationRequest = {
    bundle_id: UUID;
    display_name: string;
    name: string;
    owner_role?: string | undefined | null;
  };

  export const AggregationEmailTemplate = zodSchemaAggregationEmailTemplate();
  export type AggregationEmailTemplate = {
    application?: Application | undefined | null;
    application_id?: UUID | undefined | null;
    body_template?: Template | undefined | null;
    body_template_id: UUID;
    created?: LocalDateTime | undefined | null;
    id?: UUID | undefined | null;
    subject_template?: Template | undefined | null;
    subject_template_id: UUID;
    subscription_type: SubscriptionType;
    updated?: LocalDateTime | undefined | null;
  };

  export const Application = zodSchemaApplication();
  export type Application = {
    bundle_id: UUID;
    created?: LocalDateTime | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: LocalDateTime | undefined | null;
  };

  export const Application1 = zodSchemaApplication1();
  export type Application1 = {
    display_name: string;
    id: UUID;
  };

  export const ApplicationDTO = zodSchemaApplicationDTO();
  export type ApplicationDTO = {
    bundle_id: UUID;
    created?: string | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    owner_role?: string | undefined | null;
  };

  export const ApplicationSettingsValue = zodSchemaApplicationSettingsValue();
  export type ApplicationSettingsValue = {
    eventTypes?:
      | {
          [x: string]: EventTypeSettingsValue;
        }
      | undefined
      | null;
  };

  export const BasicAuthentication = zodSchemaBasicAuthentication();
  export type BasicAuthentication = {
    password?: string | undefined | null;
    username?: string | undefined | null;
  };

  export const BehaviorGroup = zodSchemaBehaviorGroup();
  export type BehaviorGroup = {
    actions?: Array<BehaviorGroupAction> | undefined | null;
    behaviors?: Array<EventTypeBehavior> | undefined | null;
    bundle?: Bundle | undefined | null;
    bundle_id: UUID;
    created?: LocalDateTime | undefined | null;
    default_behavior?: boolean | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    updated?: LocalDateTime | undefined | null;
  };

  export const BehaviorGroupAction = zodSchemaBehaviorGroupAction();
  export type BehaviorGroupAction = {
    created?: LocalDateTime | undefined | null;
    endpoint?: Endpoint | undefined | null;
    id?: BehaviorGroupActionId | undefined | null;
  };

  export const BehaviorGroupActionId = zodSchemaBehaviorGroupActionId();
  export type BehaviorGroupActionId = {
    behaviorGroupId: UUID;
    endpointId: UUID;
  };

  export const Bundle = zodSchemaBundle();
  export type Bundle = {
    created?: LocalDateTime | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: LocalDateTime | undefined | null;
  };

  export const BundleSettingsValue = zodSchemaBundleSettingsValue();
  export type BundleSettingsValue = {
    applications?:
      | {
          [x: string]: ApplicationSettingsValue;
        }
      | undefined
      | null;
  };

  export const CamelProperties = zodSchemaCamelProperties();
  export type CamelProperties = {
    basic_authentication?: BasicAuthentication | undefined | null;
    bearer_authentication?: string | undefined | null;
    bearer_authentication_sources_id?: number | undefined | null;
    disable_ssl_verification: boolean;
    extras?:
      | {
          [x: string]: string;
        }
      | undefined
      | null;
    secret_token?: string | undefined | null;
    url: string;
  };

  export const CreateBehaviorGroupRequest =
    zodSchemaCreateBehaviorGroupRequest();
  export type CreateBehaviorGroupRequest = {
    bundle_id?: UUID | undefined | null;
    bundle_name?: string | undefined | null;
    bundle_uuid_or_bundle_name_valid?: boolean | undefined | null;
    display_name: string;
    endpoint_ids?: Array<string> | undefined | null;
    event_type_ids?: Array<string> | undefined | null;
  };

  export const CreateBehaviorGroupResponse =
    zodSchemaCreateBehaviorGroupResponse();
  export type CreateBehaviorGroupResponse = {
    bundle_id: UUID;
    created: LocalDateTime;
    display_name: string;
    endpoints: Array<string>;
    event_types: Array<string>;
    id: UUID;
  };

  export const CurrentStatus = zodSchemaCurrentStatus();
  export type CurrentStatus = {
    end_time?: LocalDateTime | undefined | null;
    start_time?: LocalDateTime | undefined | null;
    status: Status;
  };

  export const DrawerEntryPayload = zodSchemaDrawerEntryPayload();
  export type DrawerEntryPayload = {
    created?: LocalDateTime | undefined | null;
    description?: string | undefined | null;
    id?: UUID | undefined | null;
    read: boolean;
    source?: string | undefined | null;
    title?: string | undefined | null;
  };

  export const DuplicateNameMigrationReport =
    zodSchemaDuplicateNameMigrationReport();
  export type DuplicateNameMigrationReport = {
    updatedBehaviorGroups?: number | undefined | null;
    updatedIntegrations?: number | undefined | null;
  };

  export const Endpoint = zodSchemaEndpoint();
  export type Endpoint = {
    created?: LocalDateTime | undefined | null;
    description: string;
    enabled?: boolean | undefined | null;
    id?: UUID | undefined | null;
    name: string;
    properties?:
      | (WebhookProperties | SystemSubscriptionProperties | CamelProperties)
      | undefined
      | null;
    server_errors?: number | undefined | null;
    status?: EndpointStatus | undefined | null;
    sub_type?: string | undefined | null;
    type: EndpointType;
    updated?: LocalDateTime | undefined | null;
  };

  export const EndpointPage = zodSchemaEndpointPage();
  export type EndpointPage = {
    data: Array<Endpoint>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const EndpointProperties = zodSchemaEndpointProperties();
  export type EndpointProperties = unknown;

  export const EndpointStatus = zodSchemaEndpointStatus();
  export type EndpointStatus =
    | 'READY'
    | 'UNKNOWN'
    | 'NEW'
    | 'PROVISIONING'
    | 'DELETING'
    | 'FAILED';

  export const EndpointTestRequest = zodSchemaEndpointTestRequest();
  export type EndpointTestRequest = {
    message: string;
  };

  export const EndpointType = zodSchemaEndpointType();
  export type EndpointType =
    | 'webhook'
    | 'email_subscription'
    | 'camel'
    | 'ansible'
    | 'drawer';

  export const Environment = zodSchemaEnvironment();
  export type Environment = 'PROD' | 'STAGE' | 'EPHEMERAL' | 'LOCAL_SERVER';

  export const EventLogEntry = zodSchemaEventLogEntry();
  export type EventLogEntry = {
    actions: Array<EventLogEntryAction>;
    application: string;
    bundle: string;
    created: LocalDateTime;
    event_type: string;
    id: UUID;
    payload?: string | undefined | null;
  };

  export const EventLogEntryAction = zodSchemaEventLogEntryAction();
  export type EventLogEntryAction = {
    details?:
      | {
          [x: string]: unknown;
        }
      | undefined
      | null;
    endpoint_id?: UUID | undefined | null;
    endpoint_sub_type?: string | undefined | null;
    endpoint_type: EndpointType;
    id: UUID;
    invocation_result: boolean;
    status: EventLogEntryActionStatus;
  };

  export const EventLogEntryActionStatus = zodSchemaEventLogEntryActionStatus();
  export type EventLogEntryActionStatus =
    | 'SENT'
    | 'SUCCESS'
    | 'PROCESSING'
    | 'FAILED'
    | 'UNKNOWN';

  export const EventType = zodSchemaEventType();
  export type EventType = {
    application?: Application | undefined | null;
    application_id: UUID;
    description?: string | undefined | null;
    display_name: string;
    fully_qualified_name?: string | undefined | null;
    id?: UUID | undefined | null;
    name: string;
    subscribed_by_default?: boolean | undefined | null;
    subscription_locked?: boolean | undefined | null;
  };

  export const EventTypeBehavior = zodSchemaEventTypeBehavior();
  export type EventTypeBehavior = {
    created?: LocalDateTime | undefined | null;
    event_type?: EventType | undefined | null;
    id?: EventTypeBehaviorId | undefined | null;
  };

  export const EventTypeBehaviorId = zodSchemaEventTypeBehaviorId();
  export type EventTypeBehaviorId = {
    behaviorGroupId: UUID;
    eventTypeId: UUID;
  };

  export const EventTypeSettingsValue = zodSchemaEventTypeSettingsValue();
  export type EventTypeSettingsValue = {
    emailSubscriptionTypes?:
      | {
          [x: string]: boolean;
        }
      | undefined
      | null;
    hasForcedEmail?: boolean | undefined | null;
  };

  export const Facet = zodSchemaFacet();
  export type Facet = {
    children?: Array<Facet> | undefined | null;
    displayName: string;
    id: string;
    name: string;
  };

  export const HttpType = zodSchemaHttpType();
  export type HttpType = 'GET' | 'POST' | 'PUT';

  export const InstantEmailTemplate = zodSchemaInstantEmailTemplate();
  export type InstantEmailTemplate = {
    body_template?: Template | undefined | null;
    body_template_id: UUID;
    created?: LocalDateTime | undefined | null;
    event_type?: EventType | undefined | null;
    event_type_id?: UUID | undefined | null;
    id?: UUID | undefined | null;
    subject_template?: Template | undefined | null;
    subject_template_id: UUID;
    updated?: LocalDateTime | undefined | null;
  };

  export const InternalApplicationUserPermission =
    zodSchemaInternalApplicationUserPermission();
  export type InternalApplicationUserPermission = {
    application_display_name: string;
    application_id: UUID;
    role: string;
  };

  export const InternalRoleAccess = zodSchemaInternalRoleAccess();
  export type InternalRoleAccess = {
    application_id: UUID;
    id?: UUID | undefined | null;
    role: string;
  };

  export const InternalUserPermissions = zodSchemaInternalUserPermissions();
  export type InternalUserPermissions = {
    applications: Array<Application1>;
    is_admin: boolean;
    roles: Array<string>;
  };

  export const LocalDate = zodSchemaLocalDate();
  export type LocalDate = string;

  export const LocalDateTime = zodSchemaLocalDateTime();
  export type LocalDateTime = string;

  export const LocalTime = zodSchemaLocalTime();
  export type LocalTime = string;

  export const MessageValidationResponse = zodSchemaMessageValidationResponse();
  export type MessageValidationResponse = {
    errors: {
      [x: string]: Array<string>;
    };
  };

  export const Meta = zodSchemaMeta();
  export type Meta = {
    count: number;
  };

  export const NotificationHistory = zodSchemaNotificationHistory();
  export type NotificationHistory = {
    created?: LocalDateTime | undefined | null;
    details?:
      | {
          [x: string]: unknown;
        }
      | undefined
      | null;
    endpointId?: UUID | undefined | null;
    endpointSubType?: string | undefined | null;
    endpointType?: EndpointType | undefined | null;
    id?: UUID | undefined | null;
    invocationResult: boolean;
    invocationTime: number;
    status: NotificationStatus;
  };

  export const NotificationStatus = zodSchemaNotificationStatus();
  export type NotificationStatus =
    | 'FAILED_INTERNAL'
    | 'FAILED_EXTERNAL'
    | 'PROCESSING'
    | 'SENT'
    | 'SUCCESS';

  export const PageBehaviorGroup = zodSchemaPageBehaviorGroup();
  export type PageBehaviorGroup = {
    data: Array<BehaviorGroup>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const PageDrawerEntryPayload = zodSchemaPageDrawerEntryPayload();
  export type PageDrawerEntryPayload = {
    data: Array<DrawerEntryPayload>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const PageEventLogEntry = zodSchemaPageEventLogEntry();
  export type PageEventLogEntry = {
    data: Array<EventLogEntry>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const PageEventType = zodSchemaPageEventType();
  export type PageEventType = {
    data: Array<EventType>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const PageNotificationHistory = zodSchemaPageNotificationHistory();
  export type PageNotificationHistory = {
    data: Array<NotificationHistory>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const RenderEmailTemplateRequest =
    zodSchemaRenderEmailTemplateRequest();
  export type RenderEmailTemplateRequest = {
    payload: string;
    template: Array<string>;
  };

  export const RequestDefaultBehaviorGroupPropertyList =
    zodSchemaRequestDefaultBehaviorGroupPropertyList();
  export type RequestDefaultBehaviorGroupPropertyList = {
    ignore_preferences: boolean;
    only_admins: boolean;
  };

  export const RequestSystemSubscriptionProperties =
    zodSchemaRequestSystemSubscriptionProperties();
  export type RequestSystemSubscriptionProperties = {
    group_id?: UUID | undefined | null;
    only_admins: boolean;
  };

  export const ServerInfo = zodSchemaServerInfo();
  export type ServerInfo = {
    environment?: Environment | undefined | null;
  };

  export const SettingsValuesByEventType = zodSchemaSettingsValuesByEventType();
  export type SettingsValuesByEventType = {
    bundles?:
      | {
          [x: string]: BundleSettingsValue;
        }
      | undefined
      | null;
  };

  export const Status = zodSchemaStatus();
  export type Status = 'UP' | 'MAINTENANCE';

  export const SubscriptionType = zodSchemaSubscriptionType();
  export type SubscriptionType = 'INSTANT' | 'DAILY' | 'DRAWER';

  export const SystemSubscriptionProperties =
    zodSchemaSystemSubscriptionProperties();
  export type SystemSubscriptionProperties = {
    group_id?: UUID | undefined | null;
    ignore_preferences: boolean;
    only_admins: boolean;
  };

  export const Template = zodSchemaTemplate();
  export type Template = {
    created?: LocalDateTime | undefined | null;
    data: string;
    description: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: LocalDateTime | undefined | null;
  };

  export const TriggerDailyDigestRequest = zodSchemaTriggerDailyDigestRequest();
  export type TriggerDailyDigestRequest = {
    application_name: string;
    bundle_name: string;
    end?: LocalDateTime | undefined | null;
    org_id: string;
    start?: LocalDateTime | undefined | null;
  };

  export const UUID = zodSchemaUUID();
  export type UUID = string;

  export const UpdateApplicationRequest = zodSchemaUpdateApplicationRequest();
  export type UpdateApplicationRequest = {
    display_name?: string | undefined | null;
    name?: string | undefined | null;
    owner_role?: string | undefined | null;
  };

  export const UpdateBehaviorGroupRequest =
    zodSchemaUpdateBehaviorGroupRequest();
  export type UpdateBehaviorGroupRequest = {
    display_name?: string | undefined | null;
    display_name_not_null_and_blank?: boolean | undefined | null;
    endpoint_ids?: Array<string> | undefined | null;
    event_type_ids?: Array<string> | undefined | null;
  };

  export const UpdateNotificationDrawerStatus =
    zodSchemaUpdateNotificationDrawerStatus();
  export type UpdateNotificationDrawerStatus = {
    notification_ids: Array<string>;
    read_status: boolean;
  };

  export const WebhookProperties = zodSchemaWebhookProperties();
  export type WebhookProperties = {
    basic_authentication?: BasicAuthentication | undefined | null;
    bearer_authentication?: string | undefined | null;
    disable_ssl_verification: boolean;
    method: HttpType;
    secret_token?: string | undefined | null;
    url: string;
  };

  export const __Empty = zodSchema__Empty();
  export type __Empty = string | undefined;

  function zodSchemaAddAccessRequest() {
      return z
      .object({
          application_id: zodSchemaUUID().optional().nullable(),
          role: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaAddApplicationRequest() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          display_name: z.string(),
          name: z.string(),
          owner_role: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaAggregationEmailTemplate() {
      return z
      .object({
          application: zodSchemaApplication().optional().nullable(),
          application_id: zodSchemaUUID().optional().nullable(),
          body_template: zodSchemaTemplate().optional().nullable(),
          body_template_id: zodSchemaUUID(),
          created: zodSchemaLocalDateTime().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          subject_template: zodSchemaTemplate().optional().nullable(),
          subject_template_id: zodSchemaUUID(),
          subscription_type: zodSchemaSubscriptionType(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaApplication() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          created: zodSchemaLocalDateTime().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaApplication1() {
      return z
      .object({
          display_name: z.string(),
          id: zodSchemaUUID(),
      })
      .nonstrict();
  }

  function zodSchemaApplicationDTO() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          created: z.string().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          owner_role: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaApplicationSettingsValue() {
      return z
      .object({
          eventTypes: z
          .record(zodSchemaEventTypeSettingsValue())
          .optional()
          .nullable(),
      })
      .nonstrict();
  }

  function zodSchemaBasicAuthentication() {
      return z
      .object({
          password: z.string().optional().nullable(),
          username: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroup() {
      return z
      .object({
          actions: z.array(zodSchemaBehaviorGroupAction()).optional().nullable(),
          behaviors: z.array(zodSchemaEventTypeBehavior()).optional().nullable(),
          bundle: zodSchemaBundle().optional().nullable(),
          bundle_id: zodSchemaUUID(),
          created: zodSchemaLocalDateTime().optional().nullable(),
          default_behavior: z.boolean().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroupAction() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          endpoint: zodSchemaEndpoint().optional().nullable(),
          id: zodSchemaBehaviorGroupActionId().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroupActionId() {
      return z
      .object({
          behaviorGroupId: zodSchemaUUID(),
          endpointId: zodSchemaUUID(),
      })
      .nonstrict();
  }

  function zodSchemaBundle() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaBundleSettingsValue() {
      return z
      .object({
          applications: z
          .record(zodSchemaApplicationSettingsValue())
          .optional()
          .nullable(),
      })
      .nonstrict();
  }

  function zodSchemaCamelProperties() {
      return z
      .object({
          basic_authentication: zodSchemaBasicAuthentication()
          .optional()
          .nullable(),
          bearer_authentication: z.string().optional().nullable(),
          bearer_authentication_sources_id: z
          .number()
          .int()
          .optional()
          .nullable(),
          disable_ssl_verification: z.boolean(),
          extras: z.record(z.string()).optional().nullable(),
          secret_token: z.string().optional().nullable(),
          url: z.string(),
      })
      .nonstrict();
  }

  function zodSchemaCreateBehaviorGroupRequest() {
      return z
      .object({
          bundle_id: zodSchemaUUID().optional().nullable(),
          bundle_name: z.string().optional().nullable(),
          bundle_uuid_or_bundle_name_valid: z.boolean().optional().nullable(),
          display_name: z.string(),
          endpoint_ids: z.array(z.string()).optional().nullable(),
          event_type_ids: z.array(z.string()).optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaCreateBehaviorGroupResponse() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          created: zodSchemaLocalDateTime(),
          display_name: z.string(),
          endpoints: z.array(z.string()),
          event_types: z.array(z.string()),
          id: zodSchemaUUID(),
      })
      .nonstrict();
  }

  function zodSchemaCurrentStatus() {
      return z
      .object({
          end_time: zodSchemaLocalDateTime().optional().nullable(),
          start_time: zodSchemaLocalDateTime().optional().nullable(),
          status: zodSchemaStatus(),
      })
      .nonstrict();
  }

  function zodSchemaDrawerEntryPayload() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          description: z.string().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          read: z.boolean(),
          source: z.string().optional().nullable(),
          title: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaDuplicateNameMigrationReport() {
      return z
      .object({
          updatedBehaviorGroups: z.number().int().optional().nullable(),
          updatedIntegrations: z.number().int().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaEndpoint() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          description: z.string(),
          enabled: z.boolean().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          properties: z
          .union([
              zodSchemaWebhookProperties(),
              zodSchemaSystemSubscriptionProperties(),
              zodSchemaCamelProperties(),
          ])
          .optional()
          .nullable(),
          server_errors: z.number().int().optional().nullable(),
          status: zodSchemaEndpointStatus().optional().nullable(),
          sub_type: z.string().optional().nullable(),
          type: zodSchemaEndpointType(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaEndpointPage() {
      return z
      .object({
          data: z.array(zodSchemaEndpoint()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaEndpointProperties() {
      return z.unknown();
  }

  function zodSchemaEndpointStatus() {
      return z.enum([
          'READY',
          'UNKNOWN',
          'NEW',
          'PROVISIONING',
          'DELETING',
          'FAILED',
      ]);
  }

  function zodSchemaEndpointTestRequest() {
      return z
      .object({
          message: z.string(),
      })
      .nonstrict();
  }

  function zodSchemaEndpointType() {
      return z.enum([
          'webhook',
          'email_subscription',
          'camel',
          'ansible',
          'drawer',
      ]);
  }

  function zodSchemaEnvironment() {
      return z.enum(['PROD', 'STAGE', 'EPHEMERAL', 'LOCAL_SERVER']);
  }

  function zodSchemaEventLogEntry() {
      return z
      .object({
          actions: z.array(zodSchemaEventLogEntryAction()),
          application: z.string(),
          bundle: z.string(),
          created: zodSchemaLocalDateTime(),
          event_type: z.string(),
          id: zodSchemaUUID(),
          payload: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaEventLogEntryAction() {
      return z
      .object({
          details: z.record(z.unknown()).optional().nullable(),
          endpoint_id: zodSchemaUUID().optional().nullable(),
          endpoint_sub_type: z.string().optional().nullable(),
          endpoint_type: zodSchemaEndpointType(),
          id: zodSchemaUUID(),
          invocation_result: z.boolean(),
          status: zodSchemaEventLogEntryActionStatus(),
      })
      .nonstrict();
  }

  function zodSchemaEventLogEntryActionStatus() {
      return z.enum(['SENT', 'SUCCESS', 'PROCESSING', 'FAILED', 'UNKNOWN']);
  }

  function zodSchemaEventType() {
      return z
      .object({
          application: zodSchemaApplication().optional().nullable(),
          application_id: zodSchemaUUID(),
          description: z.string().optional().nullable(),
          display_name: z.string(),
          fully_qualified_name: z.string().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          subscribed_by_default: z.boolean().optional().nullable(),
          subscription_locked: z.boolean().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaEventTypeBehavior() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          event_type: zodSchemaEventType().optional().nullable(),
          id: zodSchemaEventTypeBehaviorId().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaEventTypeBehaviorId() {
      return z
      .object({
          behaviorGroupId: zodSchemaUUID(),
          eventTypeId: zodSchemaUUID(),
      })
      .nonstrict();
  }

  function zodSchemaEventTypeSettingsValue() {
      return z
      .object({
          emailSubscriptionTypes: z.record(z.boolean()).optional().nullable(),
          hasForcedEmail: z.boolean().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaFacet() {
      return z
      .object({
          children: z
          .array(z.lazy(() => zodSchemaFacet()))
          .optional()
          .nullable(),
          displayName: z.string(),
          id: z.string(),
          name: z.string(),
      })
      .nonstrict();
  }

  function zodSchemaHttpType() {
      return z.enum(['GET', 'POST', 'PUT']);
  }

  function zodSchemaInstantEmailTemplate() {
      return z
      .object({
          body_template: zodSchemaTemplate().optional().nullable(),
          body_template_id: zodSchemaUUID(),
          created: zodSchemaLocalDateTime().optional().nullable(),
          event_type: zodSchemaEventType().optional().nullable(),
          event_type_id: zodSchemaUUID().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          subject_template: zodSchemaTemplate().optional().nullable(),
          subject_template_id: zodSchemaUUID(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaInternalApplicationUserPermission() {
      return z
      .object({
          application_display_name: z.string(),
          application_id: zodSchemaUUID(),
          role: z.string(),
      })
      .nonstrict();
  }

  function zodSchemaInternalRoleAccess() {
      return z
      .object({
          application_id: zodSchemaUUID(),
          id: zodSchemaUUID().optional().nullable(),
          role: z.string(),
      })
      .nonstrict();
  }

  function zodSchemaInternalUserPermissions() {
      return z
      .object({
          applications: z.array(zodSchemaApplication1()),
          is_admin: z.boolean(),
          roles: z.array(z.string()),
      })
      .nonstrict();
  }

  function zodSchemaLocalDate() {
      return z.string();
  }

  function zodSchemaLocalDateTime() {
      return z.string();
  }

  function zodSchemaLocalTime() {
      return z.string();
  }

  function zodSchemaMessageValidationResponse() {
      return z
      .object({
          errors: z.record(z.array(z.string())),
      })
      .nonstrict();
  }

  function zodSchemaMeta() {
      return z
      .object({
          count: z.number().int(),
      })
      .nonstrict();
  }

  function zodSchemaNotificationHistory() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          details: z.record(z.unknown()).optional().nullable(),
          endpointId: zodSchemaUUID().optional().nullable(),
          endpointSubType: z.string().optional().nullable(),
          endpointType: zodSchemaEndpointType().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          invocationResult: z.boolean(),
          invocationTime: z.number().int(),
          status: zodSchemaNotificationStatus(),
      })
      .nonstrict();
  }

  function zodSchemaNotificationStatus() {
      return z.enum([
          'FAILED_INTERNAL',
          'FAILED_EXTERNAL',
          'PROCESSING',
          'SENT',
          'SUCCESS',
      ]);
  }

  function zodSchemaPageBehaviorGroup() {
      return z
      .object({
          data: z.array(zodSchemaBehaviorGroup()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaPageDrawerEntryPayload() {
      return z
      .object({
          data: z.array(zodSchemaDrawerEntryPayload()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaPageEventLogEntry() {
      return z
      .object({
          data: z.array(zodSchemaEventLogEntry()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaPageEventType() {
      return z
      .object({
          data: z.array(zodSchemaEventType()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaPageNotificationHistory() {
      return z
      .object({
          data: z.array(zodSchemaNotificationHistory()),
          links: z.record(z.string()),
          meta: zodSchemaMeta(),
      })
      .nonstrict();
  }

  function zodSchemaRenderEmailTemplateRequest() {
      return z
      .object({
          payload: z.string(),
          template: z.array(z.string()),
      })
      .nonstrict();
  }

  function zodSchemaRequestDefaultBehaviorGroupPropertyList() {
      return z
      .object({
          ignore_preferences: z.boolean(),
          only_admins: z.boolean(),
      })
      .nonstrict();
  }

  function zodSchemaRequestSystemSubscriptionProperties() {
      return z
      .object({
          group_id: zodSchemaUUID().optional().nullable(),
          only_admins: z.boolean(),
      })
      .nonstrict();
  }

  function zodSchemaServerInfo() {
      return z
      .object({
          environment: zodSchemaEnvironment().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaSettingsValuesByEventType() {
      return z
      .object({
          bundles: z.record(zodSchemaBundleSettingsValue()).optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaStatus() {
      return z.enum(['UP', 'MAINTENANCE']);
  }

  function zodSchemaSubscriptionType() {
      return z.enum(['INSTANT', 'DAILY', 'DRAWER']);
  }

  function zodSchemaSystemSubscriptionProperties() {
      return z
      .object({
          group_id: zodSchemaUUID().optional().nullable(),
          ignore_preferences: z.boolean(),
          only_admins: z.boolean(),
      })
      .nonstrict();
  }

  function zodSchemaTemplate() {
      return z
      .object({
          created: zodSchemaLocalDateTime().optional().nullable(),
          data: z.string(),
          description: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          updated: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaTriggerDailyDigestRequest() {
      return z
      .object({
          application_name: z.string(),
          bundle_name: z.string(),
          end: zodSchemaLocalDateTime().optional().nullable(),
          org_id: z.string(),
          start: zodSchemaLocalDateTime().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaUUID() {
      return z.string();
  }

  function zodSchemaUpdateApplicationRequest() {
      return z
      .object({
          display_name: z.string().optional().nullable(),
          name: z.string().optional().nullable(),
          owner_role: z.string().optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaUpdateBehaviorGroupRequest() {
      return z
      .object({
          display_name: z.string().optional().nullable(),
          display_name_not_null_and_blank: z.boolean().optional().nullable(),
          endpoint_ids: z.array(z.string()).optional().nullable(),
          event_type_ids: z.array(z.string()).optional().nullable(),
      })
      .nonstrict();
  }

  function zodSchemaUpdateNotificationDrawerStatus() {
      return z
      .object({
          notification_ids: z.array(z.string()),
          read_status: z.boolean(),
      })
      .nonstrict();
  }

  function zodSchemaWebhookProperties() {
      return z
      .object({
          basic_authentication: zodSchemaBasicAuthentication()
          .optional()
          .nullable(),
          bearer_authentication: z.string().optional().nullable(),
          disable_ssl_verification: z.boolean(),
          method: zodSchemaHttpType(),
          secret_token: z.string().optional().nullable(),
          url: z.string(),
      })
      .nonstrict();
  }

  function zodSchema__Empty() {
      return z.string().max(0).optional();
  }
}

export namespace Operations {
  // GET /
  export namespace InternalResourceHttpRoot {
    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /access
  export namespace InternalPermissionResourceGetAccessList {
    const Response200 = z.array(Schemas.InternalApplicationUserPermission);
    type Response200 = Array<Schemas.InternalApplicationUserPermission>;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './access';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /access
  export namespace InternalPermissionResourceAddAccess {
    export interface Params {
      body: Schemas.AddAccessRequest;
    }

    export type Payload =
      | ValidatedResponse<'InternalRoleAccess', 200, Schemas.InternalRoleAccess>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './access';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.InternalRoleAccess,
                    'InternalRoleAccess',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /access/me
  export namespace InternalPermissionResourceGetPermissions {
    export type Payload =
      | ValidatedResponse<
          'InternalUserPermissions',
          200,
          Schemas.InternalUserPermissions
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './access/me';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.InternalUserPermissions,
                    'InternalUserPermissions',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /access/{internalRoleAccessId}
  export namespace InternalPermissionResourceDeleteAccess {
    export interface Params {
      internalRoleAccessId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './access/{internalRoleAccessId}'.replace(
            '{internalRoleAccessId}',
            params['internalRoleAccessId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /admin
  export namespace AdminResourceDebugRbac {
    const Rhid = z.string();
    type Rhid = string;
    export interface Params {
      rhid?: Rhid;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './admin';
        const query = {} as Record<string, any>;
        if (params['rhid'] !== undefined) {
            query['rhid'] = params['rhid'];
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /admin/status
  export namespace AdminResourceSetAdminDown {
    const Status = z.string();
    type Status = string;
    export interface Params {
      status?: Status;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './admin/status';
        const query = {} as Record<string, any>;
        if (params['status'] !== undefined) {
            query['status'] = params['status'];
        }

        return actionBuilder('POST', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /admin/templates/migrate
  export namespace AdminResourceMigrate {
    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './admin/templates/migrate';
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /admin/templates/migrate
  export namespace AdminResourceDeleteAllTemplates {
    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './admin/templates/migrate';
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /applications
  export namespace InternalResourceCreateApplication {
    export interface Params {
      body: Schemas.AddApplicationRequest;
    }

    export type Payload =
      | ValidatedResponse<'ApplicationDTO', 200, Schemas.ApplicationDTO>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.ApplicationDTO, 'ApplicationDTO', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /applications/{appId}
  export namespace InternalResourceGetApplication {
    export interface Params {
      appId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'ApplicationDTO', 200, Schemas.ApplicationDTO>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications/{appId}'.replace(
            '{appId}',
            params['appId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.ApplicationDTO, 'ApplicationDTO', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /applications/{appId}
  export namespace InternalResourceUpdateApplication {
    export interface Params {
      appId: Schemas.UUID;
      body: Schemas.UpdateApplicationRequest;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications/{appId}'.replace(
            '{appId}',
            params['appId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /applications/{appId}
  export namespace InternalResourceDeleteApplication {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      appId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications/{appId}'.replace(
            '{appId}',
            params['appId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /applications/{appId}/eventTypes
  export namespace InternalResourceGetEventTypes {
    const Response200 = z.array(Schemas.EventType);
    type Response200 = Array<Schemas.EventType>;
    export interface Params {
      appId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications/{appId}/eventTypes'.replace(
            '{appId}',
            params['appId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /behaviorGroups/default
  export namespace InternalResourceGetDefaultBehaviorGroups {
    const Response200 = z.array(Schemas.BehaviorGroup);
    type Response200 = Array<Schemas.BehaviorGroup>;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './behaviorGroups/default';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /behaviorGroups/default
  export namespace InternalResourceCreateDefaultBehaviorGroup {
    export interface Params {
      body: Schemas.BehaviorGroup;
    }

    export type Payload =
      | ValidatedResponse<'BehaviorGroup', 200, Schemas.BehaviorGroup>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './behaviorGroups/default';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.BehaviorGroup, 'BehaviorGroup', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{behaviorGroupId}/actions
  // Update the list of actions of a default behavior group.
  export namespace InternalResourceUpdateDefaultBehaviorGroupActions {
    const Body = z.array(Schemas.RequestDefaultBehaviorGroupPropertyList);
    type Body = Array<Schemas.RequestDefaultBehaviorGroupPropertyList>;
    const Response200 = z.string();
    type Response200 = string;
    export interface Params {
      behaviorGroupId: Schemas.UUID;
      body: Body;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './behaviorGroups/default/{behaviorGroupId}/actions'.replace(
            '{behaviorGroupId}',
            params['behaviorGroupId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}
  // Links the default behavior group to the event type.
  export namespace InternalResourceLinkDefaultBehaviorToEventType {
    const Response200 = z.string();
    type Response200 = string;
    export interface Params {
      behaviorGroupId: Schemas.UUID;
      eventTypeId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path =
        './behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}'
        .replace('{behaviorGroupId}', params['behaviorGroupId'].toString())
        .replace('{eventTypeId}', params['eventTypeId'].toString());
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}
  // Unlinks the default behavior group from the event type.
  export namespace InternalResourceUnlinkDefaultBehaviorToEventType {
    const Response200 = z.string();
    type Response200 = string;
    export interface Params {
      behaviorGroupId: Schemas.UUID;
      eventTypeId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path =
        './behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}'
        .replace('{behaviorGroupId}', params['behaviorGroupId'].toString())
        .replace('{eventTypeId}', params['eventTypeId'].toString());
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{id}
  // Update a default behavior group.
  export namespace InternalResourceUpdateDefaultBehaviorGroup {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      id: Schemas.UUID;
      body: Schemas.BehaviorGroup;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './behaviorGroups/default/{id}'.replace(
            '{id}',
            params['id'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /behaviorGroups/default/{id}
  // Deletes a default behavior group.
  export namespace InternalResourceDeleteDefaultBehaviorGroup {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      id: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './behaviorGroups/default/{id}'.replace(
            '{id}',
            params['id'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /bundles
  export namespace InternalResourceGetBundles {
    const Response200 = z.array(Schemas.Bundle);
    type Response200 = Array<Schemas.Bundle>;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './bundles';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /bundles
  export namespace InternalResourceCreateBundle {
    export interface Params {
      body: Schemas.Bundle;
    }

    export type Payload =
      | ValidatedResponse<'Bundle', 200, Schemas.Bundle>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './bundles';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.Bundle, 'Bundle', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /bundles/{bundleId}
  export namespace InternalResourceGetBundle {
    export interface Params {
      bundleId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'Bundle', 200, Schemas.Bundle>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './bundles/{bundleId}'.replace(
            '{bundleId}',
            params['bundleId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.Bundle, 'Bundle', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /bundles/{bundleId}
  export namespace InternalResourceUpdateBundle {
    export interface Params {
      bundleId: Schemas.UUID;
      body: Schemas.Bundle;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './bundles/{bundleId}'.replace(
            '{bundleId}',
            params['bundleId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /bundles/{bundleId}
  export namespace InternalResourceDeleteBundle {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      bundleId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './bundles/{bundleId}'.replace(
            '{bundleId}',
            params['bundleId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /bundles/{bundleId}/applications
  export namespace InternalResourceGetApplications {
    const Response200 = z.array(Schemas.Application);
    type Response200 = Array<Schemas.Application>;
    export interface Params {
      bundleId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './bundles/{bundleId}/applications'.replace(
            '{bundleId}',
            params['bundleId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /daily-digest/time-preference/{orgId}
  export namespace InternalResourceGetDailyDigestTimePreference {
    const OrgId = z.string();
    type OrgId = string;
    export interface Params {
      orgId: OrgId;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './daily-digest/time-preference/{orgId}'.replace(
            '{orgId}',
            params['orgId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /daily-digest/time-preference/{orgId}
  export namespace InternalResourceSaveDailyDigestTimePreference {
    const OrgId = z.string();
    type OrgId = string;
    export interface Params {
      orgId: OrgId;
      body: Schemas.LocalTime;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './daily-digest/time-preference/{orgId}'.replace(
            '{orgId}',
            params['orgId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /daily-digest/trigger
  export namespace InternalResourceTriggerDailyDigest {
    export interface Params {
      body: Schemas.TriggerDailyDigestRequest;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 201, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './daily-digest/trigger';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 201),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /duplicate-name-migration
  export namespace DuplicateNameMigrationResourceMigrateDuplicateNames {
    const Ack = z.string();
    type Ack = string;
    export interface Params {
      ack?: Ack;
    }

    export type Payload =
      | ValidatedResponse<
          'DuplicateNameMigrationReport',
          200,
          Schemas.DuplicateNameMigrationReport
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './duplicate-name-migration';
        const query = {} as Record<string, any>;
        if (params['ack'] !== undefined) {
            query['ack'] = params['ack'];
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.DuplicateNameMigrationReport,
                    'DuplicateNameMigrationReport',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /eventTypes
  export namespace InternalResourceCreateEventType {
    export interface Params {
      body: Schemas.EventType;
    }

    export type Payload =
      | ValidatedResponse<'EventType', 200, Schemas.EventType>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './eventTypes';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.EventType, 'EventType', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /eventTypes/{eventTypeId}
  export namespace InternalResourceUpdateEventType {
    export interface Params {
      eventTypeId: Schemas.UUID;
      body: Schemas.EventType;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './eventTypes/{eventTypeId}'.replace(
            '{eventTypeId}',
            params['eventTypeId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /eventTypes/{eventTypeId}
  export namespace InternalResourceDeleteEventType {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      eventTypeId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './eventTypes/{eventTypeId}'.replace(
            '{eventTypeId}',
            params['eventTypeId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /serverInfo
  export namespace InternalResourceGetServerInfo {
    export type Payload =
      | ValidatedResponse<'ServerInfo', 200, Schemas.ServerInfo>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './serverInfo';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.ServerInfo, 'ServerInfo', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /sources-migration
  export namespace SourcesSecretsMigrationServiceMigrateEndpointSecretsSources {
    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './sources-migration';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /status
  export namespace InternalResourceSetCurrentStatus {
    export interface Params {
      body: Schemas.CurrentStatus;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 204, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './status';
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates
  export namespace TemplateResourceGetAllTemplates {
    const Response200 = z.array(Schemas.Template);
    type Response200 = Array<Schemas.Template>;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './templates';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /templates
  export namespace TemplateResourceCreateTemplate {
    export interface Params {
      body: Schemas.Template;
    }

    export type Payload =
      | ValidatedResponse<'Template', 200, Schemas.Template>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.Template, 'Template', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/aggregation
  export namespace TemplateResourceGetAllAggregationEmailTemplates {
    const Response200 = z.array(Schemas.AggregationEmailTemplate);
    type Response200 = Array<Schemas.AggregationEmailTemplate>;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './templates/email/aggregation';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /templates/email/aggregation
  export namespace TemplateResourceCreateAggregationEmailTemplate {
    export interface Params {
      body: Schemas.AggregationEmailTemplate;
    }

    export type Payload =
      | ValidatedResponse<
          'AggregationEmailTemplate',
          200,
          Schemas.AggregationEmailTemplate
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/aggregation';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.AggregationEmailTemplate,
                    'AggregationEmailTemplate',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/aggregation/application/{appId}
  export namespace TemplateResourceGetAggregationEmailTemplatesByApplication {
    const Response200 = z.array(Schemas.AggregationEmailTemplate);
    type Response200 = Array<Schemas.AggregationEmailTemplate>;
    export interface Params {
      appId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/aggregation/application/{appId}'.replace(
            '{appId}',
            params['appId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/aggregation/{templateId}
  export namespace TemplateResourceGetAggregationemailTemplate {
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<
          'AggregationEmailTemplate',
          200,
          Schemas.AggregationEmailTemplate
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/aggregation/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.AggregationEmailTemplate,
                    'AggregationEmailTemplate',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /templates/email/aggregation/{templateId}
  export namespace TemplateResourceUpdateAggregationEmailTemplate {
    export interface Params {
      templateId: Schemas.UUID;
      body: Schemas.AggregationEmailTemplate;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/aggregation/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /templates/email/aggregation/{templateId}
  export namespace TemplateResourceDeleteAggregationEmailTemplate {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/aggregation/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/instant
  export namespace TemplateResourceGetAllInstantEmailTemplates {
    const Response200 = z.array(Schemas.InstantEmailTemplate);
    type Response200 = Array<Schemas.InstantEmailTemplate>;
    export interface Params {
      applicationId?: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant';
        const query = {} as Record<string, any>;
        if (params['applicationId'] !== undefined) {
            query['applicationId'] = params['applicationId'];
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /templates/email/instant
  export namespace TemplateResourceCreateInstantEmailTemplate {
    export interface Params {
      body: Schemas.InstantEmailTemplate;
    }

    export type Payload =
      | ValidatedResponse<
          'InstantEmailTemplate',
          200,
          Schemas.InstantEmailTemplate
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.InstantEmailTemplate,
                    'InstantEmailTemplate',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/instant/eventType/{eventTypeId}
  export namespace TemplateResourceGetInstantEmailTemplateByEventType {
    const Response404 = z.string();
    type Response404 = string;
    export interface Params {
      eventTypeId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<
          'InstantEmailTemplate',
          200,
          Schemas.InstantEmailTemplate
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', 404, Response404>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant/eventType/{eventTypeId}'.replace(
            '{eventTypeId}',
            params['eventTypeId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.InstantEmailTemplate,
                    'InstantEmailTemplate',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
                new ValidateRule(Response404, 'unknown', 404),
            ],
        })
        .build();
    };
  }
  // GET /templates/email/instant/{templateId}
  export namespace TemplateResourceGetInstantEmailTemplate {
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<
          'InstantEmailTemplate',
          200,
          Schemas.InstantEmailTemplate
        >
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.InstantEmailTemplate,
                    'InstantEmailTemplate',
                    200
                ),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /templates/email/instant/{templateId}
  export namespace TemplateResourceUpdateInstantEmailTemplate {
    export interface Params {
      templateId: Schemas.UUID;
      body: Schemas.InstantEmailTemplate;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /templates/email/instant/{templateId}
  export namespace TemplateResourceDeleteInstantEmailTemplate {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/instant/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // POST /templates/email/render
  export namespace TemplateResourceRenderEmailTemplate {
    const Response200 = z
    .object({
        result: z.array(z.string()).optional().nullable(),
    })
    .nonstrict();
    type Response200 = {
      result?: Array<string> | undefined | null;
    };
    const Response400 = z
    .object({
        message: z.string().optional().nullable(),
    })
    .nonstrict();
    type Response400 = {
      message?: string | undefined | null;
    };
    export interface Params {
      body: Schemas.RenderEmailTemplateRequest;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'unknown', 400, Response400>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/email/render';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Response400, 'unknown', 400),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /templates/{templateId}
  export namespace TemplateResourceGetTemplate {
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'Template', 200, Schemas.Template>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.Template, 'Template', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // PUT /templates/{templateId}
  export namespace TemplateResourceUpdateTemplate {
    export interface Params {
      templateId: Schemas.UUID;
      body: Schemas.Template;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // DELETE /templates/{templateId}
  export namespace TemplateResourceDeleteTemplate {
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      templateId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './templates/{templateId}'.replace(
            '{templateId}',
            params['templateId'].toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
  // GET /validation/baet
  export namespace ValidationResourceValidate {
    const Application = z.string();
    type Application = string;
    const Bundle = z.string();
    type Bundle = string;
    const EventType = z.string();
    type EventType = string;
    export interface Params {
      application?: Application;
      bundle?: Bundle;
      eventType?: EventType;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 400, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './validation/baet';
        const query = {} as Record<string, any>;
        if (params['application'] !== undefined) {
            query['application'] = params['application'];
        }

        if (params['bundle'] !== undefined) {
            query['bundle'] = params['bundle'];
        }

        if (params['eventType'] !== undefined) {
            query['eventType'] = params['eventType'];
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 400),
            ],
        })
        .build();
    };
  }
  // POST /validation/console-cloud-event
  export namespace ValidationResourceValidateConsoleCloudEvent {
    const Body = z.string();
    type Body = string;
    export interface Params {
      body: Body;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<
          'MessageValidationResponse',
          400,
          Schemas.MessageValidationResponse
        >
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './validation/console-cloud-event';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(
                    Schemas.MessageValidationResponse,
                    'MessageValidationResponse',
                    400
                ),
            ],
        })
        .build();
    };
  }
  // POST /validation/message
  export namespace ValidationResourceValidateMessage {
    const Body = z.string();
    type Body = string;
    export interface Params {
      body: Body;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<
          'MessageValidationResponse',
          400,
          Schemas.MessageValidationResponse
        >
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './validation/message';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(
                    Schemas.MessageValidationResponse,
                    'MessageValidationResponse',
                    400
                ),
            ],
        })
        .build();
    };
  }
  // GET /version
  export namespace InternalResourceGetVersion {
    const Response200 = z.string();
    type Response200 = string;
    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = './version';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403),
            ],
        })
        .build();
    };
  }
}

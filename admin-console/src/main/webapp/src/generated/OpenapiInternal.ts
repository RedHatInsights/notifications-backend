/* eslint-disable */
/**
 * Generated code, DO NOT modify directly.
 */
import { ValidatedResponse } from 'openapi2typescript';
import { ValidateRule } from 'openapi2typescript';
import {
    actionBuilder,
    ActionValidatableConfig
} from 'openapi2typescript/react-fetching-library';
import { Action } from 'react-fetching-library';
import * as z from 'zod';

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

  export const Application = zodSchemaApplication();
  export type Application = {
    bundle_id: UUID;
    created?: string | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: string | undefined | null;
  };

  export const Application1 = zodSchemaApplication1();
  export type Application1 = {
    display_name: string;
    id: string;
  };

  export const BasicAuthentication = zodSchemaBasicAuthentication();
  export type BasicAuthentication = {
    password?: string | undefined | null;
    username?: string | undefined | null;
  };

  export const BehaviorGroup = zodSchemaBehaviorGroup();
  export type BehaviorGroup = {
    actions?: Array<BehaviorGroupAction> | undefined | null;
    bundle?: Bundle | undefined | null;
    bundle_id: UUID;
    created?: string | undefined | null;
    default_behavior?: boolean | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    updated?: string | undefined | null;
  };

  export const BehaviorGroupAction = zodSchemaBehaviorGroupAction();
  export type BehaviorGroupAction = {
    created?: string | undefined | null;
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
    created?: string | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: string | undefined | null;
  };

  export const CamelProperties = zodSchemaCamelProperties();
  export type CamelProperties = {
    basic_authentication?: BasicAuthentication | undefined | null;
    disable_ssl_verification: boolean;
    extras?:
      | {
          [x: string]: string;
        }
      | undefined
      | null;
    secret_token?: string | undefined | null;
    sub_type?: string | undefined | null;
    url: string;
  };

  export const CurrentStatus = zodSchemaCurrentStatus();
  export type CurrentStatus = {
    end_time?: string | undefined | null;
    start_time?: string | undefined | null;
    status: Status;
  };

  export const EmailSubscriptionProperties =
    zodSchemaEmailSubscriptionProperties();
  export type EmailSubscriptionProperties = {
    group_id?: UUID | undefined | null;
    ignore_preferences: boolean;
    only_admins: boolean;
  };

  export const EmailSubscriptionType = zodSchemaEmailSubscriptionType();
  export type EmailSubscriptionType = 'INSTANT' | 'DAILY';

  export const Endpoint = zodSchemaEndpoint();
  export type Endpoint = {
    created?: string | undefined | null;
    description: string;
    enabled?: boolean | undefined | null;
    id?: UUID | undefined | null;
    name: string;
    properties?:
      | (WebhookProperties | EmailSubscriptionProperties | CamelProperties)
      | undefined
      | null;
    sub_type?: string | undefined | null;
    type: EndpointType;
    updated?: string | undefined | null;
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

  export const EndpointType = zodSchemaEndpointType();
  export type EndpointType =
    | 'webhook'
    | 'email_subscription'
    | 'default'
    | 'camel';

  export const Environment = zodSchemaEnvironment();
  export type Environment = 'PROD' | 'STAGE' | 'EPHEMERAL' | 'LOCAL_SERVER';

  export const EventLogEntry = zodSchemaEventLogEntry();
  export type EventLogEntry = {
    actions: Array<EventLogEntryAction>;
    application: string;
    bundle: string;
    created: string;
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
  };

  export const EventType = zodSchemaEventType();
  export type EventType = {
    application?: Application | undefined | null;
    application_id: UUID;
    description?: string | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
  };

  export const Facet = zodSchemaFacet();
  export type Facet = {
    displayName: string;
    id: string;
    name: string;
  };

  export const HttpType = zodSchemaHttpType();
  export type HttpType = 'GET' | 'POST' | 'PUT';

  export const InternalApplicationUserPermission =
    zodSchemaInternalApplicationUserPermission();
  export type InternalApplicationUserPermission = {
    application_display_name: string;
    application_id: string;
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

  export const Meta = zodSchemaMeta();
  export type Meta = {
    count: number;
  };

  export const NotificationHistory = zodSchemaNotificationHistory();
  export type NotificationHistory = {
    created?: string | undefined | null;
    details?:
      | {
          [x: string]: unknown;
        }
      | undefined
      | null;
    endpointId?: UUID | undefined | null;
    id?: UUID | undefined | null;
    invocationResult: boolean;
    invocationTime: number;
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

  export const RbacRaw = zodSchemaRbacRaw();
  export type RbacRaw = {
    data?:
      | Array<{
          [x: string]: unknown;
        }>
      | undefined
      | null;
    links?:
      | {
          [x: string]: string;
        }
      | undefined
      | null;
    meta?:
      | {
          [x: string]: number;
        }
      | undefined
      | null;
  };

  export const RenderEmailTemplateRequest =
    zodSchemaRenderEmailTemplateRequest();
  export type RenderEmailTemplateRequest = {
    body_template: string;
    payload: string;
    subject_template: string;
  };

  export const RequestDefaultBehaviorGroupPropertyList =
    zodSchemaRequestDefaultBehaviorGroupPropertyList();
  export type RequestDefaultBehaviorGroupPropertyList = {
    ignore_preferences: boolean;
    only_admins: boolean;
  };

  export const RequestEmailSubscriptionProperties =
    zodSchemaRequestEmailSubscriptionProperties();
  export type RequestEmailSubscriptionProperties = {
    only_admins: boolean;
  };

  export const ServerInfo = zodSchemaServerInfo();
  export type ServerInfo = {
    environment?: Environment | undefined | null;
  };

  export const Status = zodSchemaStatus();
  export type Status = 'UP' | 'MAINTENANCE';

  export const UUID = zodSchemaUUID();
  export type UUID = string;

  export const WebhookProperties = zodSchemaWebhookProperties();
  export type WebhookProperties = {
    basic_authentication?: BasicAuthentication | undefined | null;
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
          role: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaAddApplicationRequest() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          display_name: z.string(),
          name: z.string(),
          owner_role: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaApplication() {
      return z
      .object({
          bundle_id: zodSchemaUUID(),
          created: z.string().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          updated: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaApplication1() {
      return z
      .object({
          display_name: z.string(),
          id: z.string()
      })
      .nonstrict();
  }

  function zodSchemaBasicAuthentication() {
      return z
      .object({
          password: z.string().optional().nullable(),
          username: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroup() {
      return z
      .object({
          actions: z.array(zodSchemaBehaviorGroupAction()).optional().nullable(),
          bundle: zodSchemaBundle().optional().nullable(),
          bundle_id: zodSchemaUUID(),
          created: z.string().optional().nullable(),
          default_behavior: z.boolean().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          updated: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroupAction() {
      return z
      .object({
          created: z.string().optional().nullable(),
          endpoint: zodSchemaEndpoint().optional().nullable(),
          id: zodSchemaBehaviorGroupActionId().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaBehaviorGroupActionId() {
      return z
      .object({
          behaviorGroupId: zodSchemaUUID(),
          endpointId: zodSchemaUUID()
      })
      .nonstrict();
  }

  function zodSchemaBundle() {
      return z
      .object({
          created: z.string().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          updated: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaCamelProperties() {
      return z
      .object({
          basic_authentication: zodSchemaBasicAuthentication()
          .optional()
          .nullable(),
          disable_ssl_verification: z.boolean(),
          extras: z.record(z.string()).optional().nullable(),
          secret_token: z.string().optional().nullable(),
          sub_type: z.string().optional().nullable(),
          url: z.string()
      })
      .nonstrict();
  }

  function zodSchemaCurrentStatus() {
      return z
      .object({
          end_time: z.string().optional().nullable(),
          start_time: z.string().optional().nullable(),
          status: zodSchemaStatus()
      })
      .nonstrict();
  }

  function zodSchemaEmailSubscriptionProperties() {
      return z
      .object({
          group_id: zodSchemaUUID().optional().nullable(),
          ignore_preferences: z.boolean(),
          only_admins: z.boolean()
      })
      .nonstrict();
  }

  function zodSchemaEmailSubscriptionType() {
      return z.enum([ 'INSTANT', 'DAILY' ]);
  }

  function zodSchemaEndpoint() {
      return z
      .object({
          created: z.string().optional().nullable(),
          description: z.string(),
          enabled: z.boolean().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string(),
          properties: z
          .union([
              zodSchemaWebhookProperties(),
              zodSchemaEmailSubscriptionProperties(),
              zodSchemaCamelProperties()
          ])
          .optional()
          .nullable(),
          sub_type: z.string().optional().nullable(),
          type: zodSchemaEndpointType(),
          updated: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaEndpointPage() {
      return z
      .object({
          data: z.array(zodSchemaEndpoint()),
          links: z.record(z.string()),
          meta: zodSchemaMeta()
      })
      .nonstrict();
  }

  function zodSchemaEndpointProperties() {
      return z.unknown();
  }

  function zodSchemaEndpointType() {
      return z.enum([ 'webhook', 'email_subscription', 'default', 'camel' ]);
  }

  function zodSchemaEnvironment() {
      return z.enum([ 'PROD', 'STAGE', 'EPHEMERAL', 'LOCAL_SERVER' ]);
  }

  function zodSchemaEventLogEntry() {
      return z
      .object({
          actions: z.array(zodSchemaEventLogEntryAction()),
          application: z.string(),
          bundle: z.string(),
          created: z.string(),
          event_type: z.string(),
          id: zodSchemaUUID(),
          payload: z.string().optional().nullable()
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
          invocation_result: z.boolean()
      })
      .nonstrict();
  }

  function zodSchemaEventType() {
      return z
      .object({
          application: zodSchemaApplication().optional().nullable(),
          application_id: zodSchemaUUID(),
          description: z.string().optional().nullable(),
          display_name: z.string(),
          id: zodSchemaUUID().optional().nullable(),
          name: z.string()
      })
      .nonstrict();
  }

  function zodSchemaFacet() {
      return z
      .object({
          displayName: z.string(),
          id: z.string(),
          name: z.string()
      })
      .nonstrict();
  }

  function zodSchemaHttpType() {
      return z.enum([ 'GET', 'POST', 'PUT' ]);
  }

  function zodSchemaInternalApplicationUserPermission() {
      return z
      .object({
          application_display_name: z.string(),
          application_id: z.string(),
          role: z.string()
      })
      .nonstrict();
  }

  function zodSchemaInternalRoleAccess() {
      return z
      .object({
          application_id: zodSchemaUUID(),
          id: zodSchemaUUID().optional().nullable(),
          role: z.string()
      })
      .nonstrict();
  }

  function zodSchemaInternalUserPermissions() {
      return z
      .object({
          applications: z.array(zodSchemaApplication1()),
          is_admin: z.boolean(),
          roles: z.array(z.string())
      })
      .nonstrict();
  }

  function zodSchemaMeta() {
      return z
      .object({
          count: z.number().int()
      })
      .nonstrict();
  }

  function zodSchemaNotificationHistory() {
      return z
      .object({
          created: z.string().optional().nullable(),
          details: z.record(z.unknown()).optional().nullable(),
          endpointId: zodSchemaUUID().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          invocationResult: z.boolean(),
          invocationTime: z.number().int()
      })
      .nonstrict();
  }

  function zodSchemaPageEventLogEntry() {
      return z
      .object({
          data: z.array(zodSchemaEventLogEntry()),
          links: z.record(z.string()),
          meta: zodSchemaMeta()
      })
      .nonstrict();
  }

  function zodSchemaPageEventType() {
      return z
      .object({
          data: z.array(zodSchemaEventType()),
          links: z.record(z.string()),
          meta: zodSchemaMeta()
      })
      .nonstrict();
  }

  function zodSchemaRbacRaw() {
      return z
      .object({
          data: z.array(z.record(z.unknown())).optional().nullable(),
          links: z.record(z.string()).optional().nullable(),
          meta: z.record(z.number().int()).optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaRenderEmailTemplateRequest() {
      return z
      .object({
          body_template: z.string(),
          payload: z.string(),
          subject_template: z.string()
      })
      .nonstrict();
  }

  function zodSchemaRequestDefaultBehaviorGroupPropertyList() {
      return z
      .object({
          ignore_preferences: z.boolean(),
          only_admins: z.boolean()
      })
      .nonstrict();
  }

  function zodSchemaRequestEmailSubscriptionProperties() {
      return z
      .object({
          only_admins: z.boolean()
      })
      .nonstrict();
  }

  function zodSchemaServerInfo() {
      return z
      .object({
          environment: zodSchemaEnvironment().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaStatus() {
      return z.enum([ 'UP', 'MAINTENANCE' ]);
  }

  function zodSchemaUUID() {
      return z.string();
  }

  function zodSchemaWebhookProperties() {
      return z
      .object({
          basic_authentication: zodSchemaBasicAuthentication()
          .optional()
          .nullable(),
          disable_ssl_verification: z.boolean(),
          method: zodSchemaHttpType(),
          secret_token: z.string().optional().nullable(),
          url: z.string()
      })
      .nonstrict();
  }

  function zodSchema__Empty() {
      return z.string().max(0).optional();
  }
}

export namespace Operations {
  // GET /
  export namespace InternalServiceHttpRoot {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /access
  export namespace InternalPermissionServiceGetAccessList {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /access
  export namespace InternalPermissionServiceAddAccess {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /access/me
  export namespace InternalPermissionServiceGetPermissions {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /access/{internalRoleAccessId}
  export namespace InternalPermissionServiceDeleteAccess {
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
            params.internalRoleAccessId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 204),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /admin
  export namespace AdminServiceDebugRbac {
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
        if (params.rhid !== undefined) {
            query.rhid = params.rhid;
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /admin/status
  export namespace AdminServiceSetAdminDown {
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
        if (params.status !== undefined) {
            query.status = params.status;
        }

        return actionBuilder('POST', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /applications
  export namespace InternalServiceCreateApplication {
    export interface Params {
      body: Schemas.AddApplicationRequest;
    }

    export type Payload =
      | ValidatedResponse<'Application', 200, Schemas.Application>
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
                new ValidateRule(Schemas.Application, 'Application', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /applications/{appId}
  export namespace InternalServiceGetApplication {
    export interface Params {
      appId: Schemas.UUID;
    }

    export type Payload =
      | ValidatedResponse<'Application', 200, Schemas.Application>
      | ValidatedResponse<'__Empty', 401, Schemas.__Empty>
      | ValidatedResponse<'__Empty', 403, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './applications/{appId}'.replace(
            '{appId}',
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.Application, 'Application', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /applications/{appId}
  export namespace InternalServiceUpdateApplication {
    export interface Params {
      appId: Schemas.UUID;
      body: Schemas.Application;
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
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /applications/{appId}
  export namespace InternalServiceDeleteApplication {
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
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /applications/{appId}/eventTypes
  export namespace InternalServiceGetEventTypes {
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
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /behaviorGroups/default
  export namespace InternalServiceGetDefaultBehaviorGroups {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /behaviorGroups/default
  export namespace InternalServiceCreateDefaultBehaviorGroup {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{behaviorGroupId}/actions
  // Update the list of actions of a default behavior group.
  export namespace InternalServiceUpdateDefaultBehaviorGroupActions {
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
            params.behaviorGroupId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}
  // Links the default behavior group to the event type.
  export namespace InternalServiceLinkDefaultBehaviorToEventType {
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
        .replace('{behaviorGroupId}', params.behaviorGroupId.toString())
        .replace('{eventTypeId}', params.eventTypeId.toString());
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}
  // Unlinks the default behavior group from the event type.
  export namespace InternalServiceUnlinkDefaultBehaviorToEventType {
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
        .replace('{behaviorGroupId}', params.behaviorGroupId.toString())
        .replace('{eventTypeId}', params.eventTypeId.toString());
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /behaviorGroups/default/{id}
  // Update a default behavior group.
  export namespace InternalServiceUpdateDefaultBehaviorGroup {
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
            params.id.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /behaviorGroups/default/{id}
  // Deletes a default behavior group.
  export namespace InternalServiceDeleteDefaultBehaviorGroup {
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
            params.id.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /bundles
  export namespace InternalServiceGetBundles {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /bundles
  export namespace InternalServiceCreateBundle {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /bundles/{bundleId}
  export namespace InternalServiceGetBundle {
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
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.Bundle, 'Bundle', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /bundles/{bundleId}
  export namespace InternalServiceUpdateBundle {
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
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /bundles/{bundleId}
  export namespace InternalServiceDeleteBundle {
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
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /bundles/{bundleId}/applications
  export namespace InternalServiceGetApplications {
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
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // POST /eventTypes
  export namespace InternalServiceCreateEventType {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /eventTypes/{eventTypeId}
  export namespace InternalServiceUpdateEventType {
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
            params.eventTypeId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(Schemas.__Empty, '__Empty', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // DELETE /eventTypes/{eventTypeId}
  export namespace InternalServiceDeleteEventType {
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
            params.eventTypeId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Response200, 'unknown', 200),
                new ValidateRule(Schemas.__Empty, '__Empty', 401),
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /serverInfo
  export namespace InternalServiceGetServerInfo {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /status
  export namespace InternalServiceSetCurrentStatus {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // PUT /template-engine/render
  export namespace TemplateEngineClient$$cdiWrapperRender {
    export interface Params {
      body: Schemas.RenderEmailTemplateRequest;
    }

    export type Payload =
      | ValidatedResponse<'__Empty', 200, Schemas.__Empty>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './template-engine/render';
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.__Empty, '__Empty', 200) ]
        })
        .build();
    };
  }
  // GET /template-engine/subscription_type_supported
  export namespace TemplateEngineClient$$cdiWrapperIsSubscriptionTypeSupported {
    const ApplicationName = z.string();
    type ApplicationName = string;
    const BundleName = z.string();
    type BundleName = string;
    const SubscriptionType = Schemas.EmailSubscriptionType;
    type SubscriptionType = Schemas.EmailSubscriptionType;
    const Response200 = z.boolean();
    type Response200 = boolean;
    export interface Params {
      applicationName: ApplicationName;
      bundleName: BundleName;
      subscriptionType: SubscriptionType;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './template-engine/subscription_type_supported';
        const query = {} as Record<string, any>;
        if (params.applicationName !== undefined) {
            query.applicationName = params.applicationName;
        }

        if (params.bundleName !== undefined) {
            query.bundleName = params.bundleName;
        }

        if (params.subscriptionType !== undefined) {
            query.subscriptionType = params.subscriptionType;
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
        })
        .build();
    };
  }
  // POST /templates/email/render
  export namespace InternalServiceRenderEmailTemplate {
    const Response200 = z
    .object({
        body: z.string().optional().nullable(),
        subject: z.string().optional().nullable()
    })
    .nonstrict();
    type Response200 = {
      body?: string | undefined | null;
      subject?: string | undefined | null;
    };
    const Response400 = z
    .object({
        message: z.string().optional().nullable()
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
  // GET /validation/baet
  export namespace ValidationEndpointValidate {
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = './validation/baet';
        const query = {} as Record<string, any>;
        if (params.application !== undefined) {
            query.application = params.application;
        }

        if (params.bundle !== undefined) {
            query.bundle = params.bundle;
        }

        if (params.eventType !== undefined) {
            query.eventType = params.eventType;
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.__Empty, '__Empty', 200) ]
        })
        .build();
    };
  }
  // GET /version
  export namespace InternalServiceGetVersion {
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
                new ValidateRule(Schemas.__Empty, '__Empty', 403)
            ]
        })
        .build();
    };
  }
}

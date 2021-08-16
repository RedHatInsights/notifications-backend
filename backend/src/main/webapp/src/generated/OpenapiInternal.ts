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
  export const Application = zodSchemaApplication();
  export type Application = {
    bundle_id: UUID;
    created?: string | undefined | null;
    display_name: string;
    id?: UUID | undefined | null;
    name: string;
    updated?: string | undefined | null;
  };

  export const AtomicLong = zodSchemaAtomicLong();
  export type AtomicLong = {
    acquire?: number | undefined | null;
    andDecrement?: number | undefined | null;
    andIncrement?: number | undefined | null;
    opaque?: number | undefined | null;
    plain?: number | undefined | null;
    release?: number | undefined | null;
    value?: number | undefined | null;
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
  export type EmailSubscriptionProperties = unknown;

  export const EmailSubscriptionType = zodSchemaEmailSubscriptionType();
  export type EmailSubscriptionType = 'DAILY' | 'INSTANT';

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

  export const EntityTag = zodSchemaEntityTag();
  export type EntityTag = {
    value?: string | undefined | null;
    weak?: boolean | undefined | null;
  };

  export const Error = zodSchemaError();
  export type Error = {
    message?: string | undefined | null;
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

  export const Family = zodSchemaFamily();
  export type Family =
    | 'CLIENT_ERROR'
    | 'INFORMATIONAL'
    | 'OTHER'
    | 'REDIRECTION'
    | 'SERVER_ERROR'
    | 'SUCCESSFUL';

  export const HttpType = zodSchemaHttpType();
  export type HttpType = 'GET' | 'POST' | 'PUT';

  export const Link = zodSchemaLink();
  export type Link = {
    params?:
      | {
          [x: string]: string;
        }
      | undefined
      | null;
    rel?: string | undefined | null;
    rels?: Array<string> | undefined | null;
    title?: string | undefined | null;
    type?: string | undefined | null;
    uri?: string | undefined | null;
    uriBuilder?: UriBuilder | undefined | null;
  };

  export const Locale = zodSchemaLocale();
  export type Locale = {
    country?: string | undefined | null;
    displayCountry?: string | undefined | null;
    displayLanguage?: string | undefined | null;
    displayName?: string | undefined | null;
    displayScript?: string | undefined | null;
    displayVariant?: string | undefined | null;
    extensionKeys?: Array<string> | undefined | null;
    iSO3Country?: string | undefined | null;
    iSO3Language?: string | undefined | null;
    language?: string | undefined | null;
    script?: string | undefined | null;
    unicodeLocaleAttributes?: Array<string> | undefined | null;
    unicodeLocaleKeys?: Array<string> | undefined | null;
    variant?: string | undefined | null;
  };

  export const MediaType = zodSchemaMediaType();
  export type MediaType = {
    parameters?:
      | {
          [x: string]: string;
        }
      | undefined
      | null;
    subtype?: string | undefined | null;
    type?: string | undefined | null;
    wildcardSubtype?: boolean | undefined | null;
    wildcardType?: boolean | undefined | null;
  };

  export const Meta = zodSchemaMeta();
  export type Meta = {
    count: number;
  };

  export const MigrationReport = zodSchemaMigrationReport();
  export type MigrationReport = {
    deletedEndpoints?: AtomicLong | undefined | null;
    durationInMs?: AtomicLong | undefined | null;
    updatedAccounts?: AtomicLong | undefined | null;
    updatedBehaviorGroupActions?: AtomicLong | undefined | null;
  };

  export const MultivaluedMapStringObject =
    zodSchemaMultivaluedMapStringObject();
  export type MultivaluedMapStringObject = {
    [x: string]: Array<unknown>;
  };

  export const MultivaluedMapStringString =
    zodSchemaMultivaluedMapStringString();
  export type MultivaluedMapStringString = {
    [x: string]: Array<string>;
  };

  export const NewCookie = zodSchemaNewCookie();
  export type NewCookie = {
    comment?: string | undefined | null;
    domain?: string | undefined | null;
    expiry?: string | undefined | null;
    httpOnly?: boolean | undefined | null;
    maxAge?: number | undefined | null;
    name?: string | undefined | null;
    path?: string | undefined | null;
    secure?: boolean | undefined | null;
    value?: string | undefined | null;
    version?: number | undefined | null;
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
    eventId?: string | undefined | null;
    id?: UUID | undefined | null;
    invocationResult: boolean;
    invocationTime: number;
  };

  export const PageRbacGroup = zodSchemaPageRbacGroup();
  export type PageRbacGroup = {
    data: Array<RbacGroup>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const PageRbacUser = zodSchemaPageRbacUser();
  export type PageRbacUser = {
    data: Array<RbacUser>;
    links: {
      [x: string]: string;
    };
    meta: Meta;
  };

  export const RbacGroup = zodSchemaRbacGroup();
  export type RbacGroup = {
    created?: string | undefined | null;
    description?: string | undefined | null;
    modified?: string | undefined | null;
    name?: string | undefined | null;
    platform_default?: boolean | undefined | null;
    principalCount?: number | undefined | null;
    roleCount?: number | undefined | null;
    system?: boolean | undefined | null;
    uuid?: UUID | undefined | null;
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

  export const RbacUser = zodSchemaRbacUser();
  export type RbacUser = {
    active?: boolean | undefined | null;
    email?: string | undefined | null;
    firstName?: string | undefined | null;
    isActive?: boolean | undefined | null;
    isOrgAdmin?: boolean | undefined | null;
    lastName?: string | undefined | null;
    orgAdmin?: boolean | undefined | null;
    username?: string | undefined | null;
  };

  export const RenderEmailTemplateRequest =
    zodSchemaRenderEmailTemplateRequest();
  export type RenderEmailTemplateRequest = {
    bodyTemplate: string;
    payload?: string | undefined | null;
    subjectTemplate: string;
  };

  export const RenderEmailTemplateResponse =
    zodSchemaRenderEmailTemplateResponse();
  export type RenderEmailTemplateResponse = {
    body?: string | undefined | null;
    subject?: string | undefined | null;
  };

  export const Response = zodSchemaResponse();
  export type Response = {
    allowedMethods?: Array<string> | undefined | null;
    cookies?:
      | {
          [x: string]: NewCookie;
        }
      | undefined
      | null;
    date?: string | undefined | null;
    entity?: unknown | undefined | null;
    entityTag?: EntityTag | undefined | null;
    headers?: MultivaluedMapStringObject | undefined | null;
    language?: Locale | undefined | null;
    lastModified?: string | undefined | null;
    length?: number | undefined | null;
    links?: Array<Link> | undefined | null;
    location?: string | undefined | null;
    mediaType?: MediaType | undefined | null;
    metadata?: MultivaluedMapStringObject | undefined | null;
    status?: number | undefined | null;
    statusInfo?: StatusType | undefined | null;
    stringHeaders?: MultivaluedMapStringString | undefined | null;
  };

  export const Status = zodSchemaStatus();
  export type Status = 'MAINTENANCE' | 'UP';

  export const StatusType = zodSchemaStatusType();
  export type StatusType = {
    family?: Family | undefined | null;
    reasonPhrase?: string | undefined | null;
    statusCode?: number | undefined | null;
  };

  export const UUID = zodSchemaUUID();
  export type UUID = string;

  export const UriBuilder = zodSchemaUriBuilder();
  export type UriBuilder = unknown;

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

  function zodSchemaAtomicLong() {
      return z
      .object({
          acquire: z.number().int().optional().nullable(),
          andDecrement: z.number().int().optional().nullable(),
          andIncrement: z.number().int().optional().nullable(),
          opaque: z.number().int().optional().nullable(),
          plain: z.number().int().optional().nullable(),
          release: z.number().int().optional().nullable(),
          value: z.number().int().optional().nullable()
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
      return z.unknown();
  }

  function zodSchemaEmailSubscriptionType() {
      return z.enum([ 'DAILY', 'INSTANT' ]);
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

  function zodSchemaEntityTag() {
      return z
      .object({
          value: z.string().optional().nullable(),
          weak: z.boolean().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaError() {
      return z
      .object({
          message: z.string().optional().nullable()
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

  function zodSchemaFamily() {
      return z.enum([
          'CLIENT_ERROR',
          'INFORMATIONAL',
          'OTHER',
          'REDIRECTION',
          'SERVER_ERROR',
          'SUCCESSFUL'
      ]);
  }

  function zodSchemaHttpType() {
      return z.enum([ 'GET', 'POST', 'PUT' ]);
  }

  function zodSchemaLink() {
      return z
      .object({
          params: z.record(z.string()).optional().nullable(),
          rel: z.string().optional().nullable(),
          rels: z.array(z.string()).optional().nullable(),
          title: z.string().optional().nullable(),
          type: z.string().optional().nullable(),
          uri: z.string().optional().nullable(),
          uriBuilder: zodSchemaUriBuilder().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaLocale() {
      return z
      .object({
          country: z.string().optional().nullable(),
          displayCountry: z.string().optional().nullable(),
          displayLanguage: z.string().optional().nullable(),
          displayName: z.string().optional().nullable(),
          displayScript: z.string().optional().nullable(),
          displayVariant: z.string().optional().nullable(),
          extensionKeys: z.array(z.string()).optional().nullable(),
          iSO3Country: z.string().optional().nullable(),
          iSO3Language: z.string().optional().nullable(),
          language: z.string().optional().nullable(),
          script: z.string().optional().nullable(),
          unicodeLocaleAttributes: z.array(z.string()).optional().nullable(),
          unicodeLocaleKeys: z.array(z.string()).optional().nullable(),
          variant: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaMediaType() {
      return z
      .object({
          parameters: z.record(z.string()).optional().nullable(),
          subtype: z.string().optional().nullable(),
          type: z.string().optional().nullable(),
          wildcardSubtype: z.boolean().optional().nullable(),
          wildcardType: z.boolean().optional().nullable()
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

  function zodSchemaMigrationReport() {
      return z
      .object({
          deletedEndpoints: zodSchemaAtomicLong().optional().nullable(),
          durationInMs: zodSchemaAtomicLong().optional().nullable(),
          updatedAccounts: zodSchemaAtomicLong().optional().nullable(),
          updatedBehaviorGroupActions: zodSchemaAtomicLong()
          .optional()
          .nullable()
      })
      .nonstrict();
  }

  function zodSchemaMultivaluedMapStringObject() {
      return z.record(z.array(z.unknown()));
  }

  function zodSchemaMultivaluedMapStringString() {
      return z.record(z.array(z.string()));
  }

  function zodSchemaNewCookie() {
      return z
      .object({
          comment: z.string().optional().nullable(),
          domain: z.string().optional().nullable(),
          expiry: z.string().optional().nullable(),
          httpOnly: z.boolean().optional().nullable(),
          maxAge: z.number().int().optional().nullable(),
          name: z.string().optional().nullable(),
          path: z.string().optional().nullable(),
          secure: z.boolean().optional().nullable(),
          value: z.string().optional().nullable(),
          version: z.number().int().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaNotificationHistory() {
      return z
      .object({
          created: z.string().optional().nullable(),
          details: z.record(z.unknown()).optional().nullable(),
          endpointId: zodSchemaUUID().optional().nullable(),
          eventId: z.string().optional().nullable(),
          id: zodSchemaUUID().optional().nullable(),
          invocationResult: z.boolean(),
          invocationTime: z.number().int()
      })
      .nonstrict();
  }

  function zodSchemaPageRbacGroup() {
      return z
      .object({
          data: z.array(zodSchemaRbacGroup()),
          links: z.record(z.string()),
          meta: zodSchemaMeta()
      })
      .nonstrict();
  }

  function zodSchemaPageRbacUser() {
      return z
      .object({
          data: z.array(zodSchemaRbacUser()),
          links: z.record(z.string()),
          meta: zodSchemaMeta()
      })
      .nonstrict();
  }

  function zodSchemaRbacGroup() {
      return z
      .object({
          created: z.string().optional().nullable(),
          description: z.string().optional().nullable(),
          modified: z.string().optional().nullable(),
          name: z.string().optional().nullable(),
          platform_default: z.boolean().optional().nullable(),
          principalCount: z.number().int().optional().nullable(),
          roleCount: z.number().int().optional().nullable(),
          system: z.boolean().optional().nullable(),
          uuid: zodSchemaUUID().optional().nullable()
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

  function zodSchemaRbacUser() {
      return z
      .object({
          active: z.boolean().optional().nullable(),
          email: z.string().optional().nullable(),
          firstName: z.string().optional().nullable(),
          isActive: z.boolean().optional().nullable(),
          isOrgAdmin: z.boolean().optional().nullable(),
          lastName: z.string().optional().nullable(),
          orgAdmin: z.boolean().optional().nullable(),
          username: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaRenderEmailTemplateRequest() {
      return z
      .object({
          bodyTemplate: z.string(),
          payload: z.string().optional().nullable(),
          subjectTemplate: z.string()
      })
      .nonstrict();
  }

  function zodSchemaRenderEmailTemplateResponse() {
      return z
      .object({
          body: z.string().optional().nullable(),
          subject: z.string().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaResponse() {
      return z
      .object({
          allowedMethods: z.array(z.string()).optional().nullable(),
          cookies: z.record(zodSchemaNewCookie()).optional().nullable(),
          date: z.string().optional().nullable(),
          entity: z.unknown().optional().nullable(),
          entityTag: zodSchemaEntityTag().optional().nullable(),
          headers: zodSchemaMultivaluedMapStringObject().optional().nullable(),
          language: zodSchemaLocale().optional().nullable(),
          lastModified: z.string().optional().nullable(),
          length: z.number().int().optional().nullable(),
          links: z.array(zodSchemaLink()).optional().nullable(),
          location: z.string().optional().nullable(),
          mediaType: zodSchemaMediaType().optional().nullable(),
          metadata: zodSchemaMultivaluedMapStringObject().optional().nullable(),
          status: z.number().int().optional().nullable(),
          statusInfo: zodSchemaStatusType().optional().nullable(),
          stringHeaders: zodSchemaMultivaluedMapStringString()
          .optional()
          .nullable()
      })
      .nonstrict();
  }

  function zodSchemaStatus() {
      return z.enum([ 'MAINTENANCE', 'UP' ]);
  }

  function zodSchemaStatusType() {
      return z
      .object({
          family: zodSchemaFamily().optional().nullable(),
          reasonPhrase: z.string().optional().nullable(),
          statusCode: z.number().int().optional().nullable()
      })
      .nonstrict();
  }

  function zodSchemaUUID() {
      return z.string();
  }

  function zodSchemaUriBuilder() {
      return z.unknown();
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = '/';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.__Empty, '__Empty', 204) ]
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
      | ValidatedResponse<'Response', 200, Schemas.Response>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/admin';
        const query = {} as Record<string, any>;
        if (params.rhid !== undefined) {
            query.rhid = params.rhid;
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.Response, 'Response', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/admin/status';
        const query = {} as Record<string, any>;
        if (params.status !== undefined) {
            query.status = params.status;
        }

        return actionBuilder('POST', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.__Empty, '__Empty', 200) ]
        })
        .build();
    };
  }
  // POST /applications
  export namespace InternalServiceCreateApplication {
    export interface Params {
      body: Schemas.Application;
    }

    export type Payload =
      | ValidatedResponse<'Application', 200, Schemas.Application>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/applications';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.Application, 'Application', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/applications/{appId}'.replace(
            '{appId}',
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.Application, 'Application', 200) ]
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
      | ValidatedResponse<'Response', 200, Schemas.Response>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/applications/{appId}'.replace(
            '{appId}',
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.Response, 'Response', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/applications/{appId}'.replace(
            '{appId}',
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/applications/{appId}/eventTypes'.replace(
            '{appId}',
            params.appId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (): ActionCreator => {
        const path = '/bundles';
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/bundles';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.Bundle, 'Bundle', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/bundles/{bundleId}'.replace(
            '{bundleId}',
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Schemas.Bundle, 'Bundle', 200) ]
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
      | ValidatedResponse<'Response', 200, Schemas.Response>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/bundles/{bundleId}'.replace(
            '{bundleId}',
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.Response, 'Response', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/bundles/{bundleId}'.replace(
            '{bundleId}',
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/bundles/{bundleId}/applications'.replace(
            '{bundleId}',
            params.bundleId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
        })
        .build();
    };
  }
  // GET /email_endpoint/migrate
  export namespace EmailEndpointMigrationServiceMigrateEmailEndpoint {
    const ConfirmationToken = z.string();
    type ConfirmationToken = string;
    export interface Params {
      confirmationToken?: ConfirmationToken;
    }

    export type Payload =
      | ValidatedResponse<'MigrationReport', 200, Schemas.MigrationReport>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/email_endpoint/migrate';
        const query = {} as Record<string, any>;
        if (params.confirmationToken !== undefined) {
            query['confirmation-token'] = params.confirmationToken;
        }

        return actionBuilder('GET', path)
        .queryParams(query)
        .config({
            rules: [
                new ValidateRule(Schemas.MigrationReport, 'MigrationReport', 200)
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/eventTypes';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Schemas.EventType, 'EventType', 200) ]
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
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/eventTypes/{eventTypeId}'.replace(
            '{eventTypeId}',
            params.eventTypeId.toString()
        );
        const query = {} as Record<string, any>;
        return actionBuilder('DELETE', path)
        .queryParams(query)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
        })
        .build();
    };
  }
  // PUT /status
  export namespace InternalServiceSetCurrentStatus {
    const Response200 = z.unknown();
    type Response200 = unknown;
    export interface Params {
      body: Schemas.CurrentStatus;
    }

    export type Payload =
      | ValidatedResponse<'unknown', 200, Response200>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/status';
        const query = {} as Record<string, any>;
        return actionBuilder('PUT', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [ new ValidateRule(Response200, 'unknown', 200) ]
        })
        .build();
    };
  }
  // POST /templates/email/render
  export namespace InternalServiceRenderEmailTemplate {
    export interface Params {
      body: Schemas.RenderEmailTemplateRequest;
    }

    export type Payload =
      | ValidatedResponse<
          'RenderEmailTemplateResponse',
          200,
          Schemas.RenderEmailTemplateResponse
        >
      | ValidatedResponse<'Error', 400, Schemas.Error>
      | ValidatedResponse<'unknown', undefined, unknown>;
    export type ActionCreator = Action<Payload, ActionValidatableConfig>;
    export const actionCreator = (params: Params): ActionCreator => {
        const path = '/templates/email/render';
        const query = {} as Record<string, any>;
        return actionBuilder('POST', path)
        .queryParams(query)
        .data(params.body)
        .config({
            rules: [
                new ValidateRule(
                    Schemas.RenderEmailTemplateResponse,
                    'RenderEmailTemplateResponse',
                    200
                ),
                new ValidateRule(Schemas.Error, 'Error', 400)
            ]
        })
        .build();
    };
  }
}

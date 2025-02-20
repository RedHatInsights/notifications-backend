#!/bin/bash

zed relationship create 'rbac/role:notifications_admin' 't_integrations_all_all' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_integrations_endpoints_all' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_integrations_all_write' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_integrations_endpoints_write' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_integrations_all_read' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_integrations_endpoints_read' 'rbac/principal:*'

zed relationship create 'rbac/role:notifications_admin' 't_notifications_all_all' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_all_read' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_all_write' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_events_all' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_events_read' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_notifications_all' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_notifications_read' 'rbac/principal:*'
zed relationship create 'rbac/role:notifications_admin' 't_notifications_notifications_write' 'rbac/principal:*'

zed relationship create 'rbac/role_binding:admins' 't_role' 'rbac/role:notifications_admin'
zed relationship create 'rbac/role_binding:admins' 't_subject' 'rbac/principal:redhat/12345'

zed relationship create 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988' 't_binding' 'rbac/role_binding:admins'
zed relationship create 'rbac/group:admins' 't_member' 'rbac/principal:redhat/12345'

# Create a few test relations.
zed relationship create 'notifications/integration:0d0c1068-4dbc-11ef-8284-13aac89bbfb2' 't_workspace' 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988'
zed relationship create 'notifications/integration:0d0c134c-4dbc-11ef-8285-276233bafbca' 't_workspace' 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988'
zed relationship create 'notifications/integration:0d0c13d8-4dbc-11ef-8286-5bb85fffe392' 't_workspace' 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988'
zed relationship create 'notifications/integration:0d0c1450-4dbc-11ef-8287-3f2e8edeb112' 't_workspace' 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988'

zed relationship create 'notifications/integration:d756e136-10a6-40a6-822f-4bb92405239f' 't_workspace' 'rbac/workspace:3c3ef849-d8ca-11ef-ad01-083a885cd988'

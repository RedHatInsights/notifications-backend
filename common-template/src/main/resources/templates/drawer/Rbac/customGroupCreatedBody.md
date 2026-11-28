{#include drawer/Common/commonDrawerNotification.md}
{#body}
{#let userLink=rbacLink:userMd(data.events[0].payload.username, environment.url, query_params)}
{#let groupLink=rbacLink:groupMd(data.events[0].payload.name, data.events[0].payload.uuid, environment.url, query_params)}
{rbac:groupCreatedBody(userLink, groupLink)}
{/let}
{/let}
{/body}
{/include}

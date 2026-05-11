{#include drawer/Common/commonDrawerNotification.md}
{#body}
**[{data.events[0].payload.username}]({environment.url}/iam/user-access/users/detail/{data.events[0].payload.username}?{query_params})** updated the Custom default access group by {#if data.events[0].payload.operation
  == "added"}adding{#else}removing{/if} the **[{data.events[0].payload.role.name}]({environment.url}/iam/user-access/roles/detail/{data.events[0].payload.role.uuid}?{query_params})** {#if data.events[0].payload.operation
  == "added"}to{#else}from{/if} the group.
{/body}
{/include}

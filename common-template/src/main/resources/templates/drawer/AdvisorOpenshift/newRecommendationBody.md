{#include drawer/Common/commonDrawerNotification.md}
{#body}
**[{data.context.display_name}]({data.context.host_url}?{query_params})** has {data.events.size()} new {#if data.events.size() is 1}recommendation{#else}recommendations{/if}.
{/body}
{/include}

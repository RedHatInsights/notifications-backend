{#include drawer/Common/commonDrawerNotification.md}
{#body}
{#let recs=data.events.size()}
{#if recs > 1}
    {recs} recommendations have recently been deactivated by Red Hat Insights and are no longer affecting your systems. [Open Advisor]({environment.url}/insights/advisor/recommendations?{query_params})
{#else}
    1 recommendation has recently been deactivated by Red Hat Insights and is no longer affecting your systems. [Open Advisor]({environment.url}/insights/advisor/recommendations/{data.events[0].payload.rule_id}?{query_params})
{/if}
{/let}
{/body}
{/include}

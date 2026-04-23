{#let recs=data.events.size()}
{#if recs > 1}
    {recs} recommendations have recently been deactivated by Red Hat Insights and are no longer affecting your systems. [Open Advisor]({environment.url}/insights/advisor)
{#else}
    1 recommendation has recently been deactivated by Red Hat Insights and is no longer affecting your systems. [Open Advisor]({environment.url}/insights/advisor)
{/if}
{/let}

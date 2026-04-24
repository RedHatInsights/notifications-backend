{#let recs=data.events.size()}
{#if recs > 1}
    {recs} recommendations have recently been deactivated by Red Hat Insights and are no longer affecting your systems. [Open Advisor]({environment.url}/insights/advisor/recommendations?from=notifications&integration=drawer)
{#else}
    1 recommendation has recently been deactivated by Red Hat Insights and is no longer affecting your systems. [Open recommendation]({environment.url}/insights/advisor/recommendations/{data.events[0].payload.rule_id}?from=notifications&integration=drawer)
{/if}
{/let}

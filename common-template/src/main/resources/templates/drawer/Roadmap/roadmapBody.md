{#include drawer/Common/commonDrawerNotification.md}
{#body}
{#let deprecationsCount=data.events[0].payload.deprecations.orEmpty.count.or(0) changesCount=data.events[0].payload.changes.orEmpty.count.or(0) additionsCount=data.events[0].payload.additions.orEmpty.count.or(0)}
{#if deprecationsCount > 0 || changesCount > 0 || additionsCount > 0}Your [roadmap monthly report]({environment.url}/insights/planning/roadmap?{query_params}) is available: **{deprecationsCount}** {#if deprecationsCount is 1}deprecation{#else}deprecations{/if}, **{changesCount}** {#if changesCount is 1}change{#else}changes{/if}, and **{additionsCount}** {#if additionsCount is 1}addition{#else}additions{/if}.{#else}Your [roadmap monthly report]({environment.url}/insights/planning/roadmap?{query_params}) is available. No roadmap changes affected your systems this month.{/if}
{/let}
{/body}
{/include}

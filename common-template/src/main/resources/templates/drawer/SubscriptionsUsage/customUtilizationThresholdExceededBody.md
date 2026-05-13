{#include drawer/Common/commonDrawerNotification.md}
{#body}
The subscription usage for product variant **[{data.context.product_id}]({environment.url}/subscriptions/usage/{data.context.product_id.urlEncode}?{query_params})** has exceeded your custom threshold with a utilization of **{data.events[0].payload.utilization_percentage}%** for the **{data.context.metric_id}** metric.
{/body}
{/include}

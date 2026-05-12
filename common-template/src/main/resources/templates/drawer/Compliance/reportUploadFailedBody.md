{#include drawer/Common/commonDrawerNotification.md}
{#body}
Your system **[{data.events[0].payload.host_name}]({environment.url}/insights/compliance/systems/{data.events[0].payload.host_id}?{query_params})** failed to upload a new compliance report. The error message returned by our system for the request *{data.events[0].payload.request_id}* was: *{data.events[0].payload.error}*
{/body}
{/include}

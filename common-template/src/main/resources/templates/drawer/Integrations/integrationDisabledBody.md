{#if data.context.error_type == "HTTP_4XX"}
    Integration **{data.context.endpoint_name}** was disabled because the remote endpoint responded with an HTTP status code {data.context.status_code}.
{#else}
    Integration **{data.context.endpoint_name}** was disabled because the connection couldn't be established with the remote endpoint, or it responded too many times with a server error (HTTP status code 5xx) after {data.context.errors_count} attempts.
    The latest error was: {data.context.error_details}{#if data.context.error_type == "HTTP_5XX"} {data.context.status_code}{/if}.
{/if}

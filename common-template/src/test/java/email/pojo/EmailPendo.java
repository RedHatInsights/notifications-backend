package email.pojo;

public class EmailPendo {
    public static String GENERAL_PENDO_MESSAGE = "You can also receive notifications on tools like Slack, Microsoft Teams, Google Chat, and more! Go to Integrations to set those up.<p><a style=\"color: rgb(0, 102, 204); text-decoration-color: rgb(0, 102, 204);\" href=\"%s/settings/integrations\" target=\"_blank\">Go to Integrations</a>  <a style=\"text-decoration: none; left-margin: 4px;\" href=\"%s/settings/integrations\" target=\"_blank\"><img src=\"https://console.redhat.com/apps/frontend-assets/email-assets/external-link-blue.png\" alt=\"\" height=\"14\" width=\"14\" style=\"vertical-align: -4px; left-margin: 4px;\" /></a></p>";
    public static String GENERAL_PENDO_TITLE = "Did you know?";

    public static final String OUTAGE_PENDO_MESSAGE = "Between Aug 22, 07:19 AM UTC and Aug 26, 12:47 PM UTC, the notifications email service experienced an outage. This email was delayed from that time period.";
    public static final String OUTAGE_PENDO_TITLE = "Outage notice";

    private String pendoTitle;
    private String pendoMessage;

    public EmailPendo(String pendoTitle, String pendoMessage) {
        this.pendoTitle = pendoTitle;
        this.pendoMessage = pendoMessage;
    }

    public String getPendoTitle() {
        return pendoTitle;
    }

    public String getPendoMessage() {
        return pendoMessage;
    }
}

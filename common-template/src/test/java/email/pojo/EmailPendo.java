package email.pojo;

public class EmailPendo {
    public static String GENERAL_PENDO_MESSAGE = "You can also receive notifications on tools like Slack, Microsoft Teams, Google chat, and more! Go to <b><a href=\"%s/settings/integrations\" target=\"_blank\">integrations</a></b> to set those up.";
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

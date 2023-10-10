package com.redhat.cloud.notifications.connector.email;

import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Individual class for testing the bug specified below but with the "single
 * email per user" option enabled.
 */
@Deprecated(forRemoval = true)
@QuarkusTest
public class EmailRouteBuilderRHCLOUD28631SingleEmailPerUserTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ProducerTemplate producerTemplate;

    /**
     * Regression test for <a href="https://issues.redhat.com/browse/RHCLOUD-28631">RHCLOUD-28631</a>
     * which, made the email connector send multiple emails for a single tenant
     * with more than one behavior group configured. This was caused because
     * the recipient settings were being processed sequentially, which in turn
     * built different sets of users, which caused an email to be sent for each
     * set. The fix consisted on aggregating the users set to reduce the number
     * of emails being sent.
     * @throws IOException if the different stubbed responses could not be
     * loaded.
     */
    @Test
    void testMultipleRecipientSettingsSameUserSendSingleEmail() throws Exception {
        // Prepare the payload that we will simulate that we receive on the
        // connector.
        final Set<String> users = Set.of("foouser", "baruser");
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            users
        );
        final Set<String> users2 = Set.of("johndoe", "janedoe");

        final RecipientSettings recipientSettings2 = new RecipientSettings(
            true,
            true,
            null,
            users2
        );

        // Create the exchange that we will use in this test.
        final Exchange exchange = this.createExchangeWithBody("");

        final List<RecipientSettings> recipientSettingsList = new ArrayList<>();
        recipientSettingsList.add(recipientSettings);
        recipientSettingsList.add(recipientSettings2);

        final List<String> subscribers = List.of("user1", "user2");

        final String emailBody = "emailBody";
        final String emailSubject = "emailSubject";
        exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailBody);
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailSubject);
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, recipientSettingsList);
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);

        // Prepare the stubbed response for the RBAC service.
        final URL url = Resources.getResource("processors/rbac/rbacUsersResponse.json");
        final String body = Resources.toString(url, StandardCharsets.UTF_8);

        // Mock the endpoints so that the whole flow is followed.
        AdviceWith.adviceWith(this.context, Routes.FETCH_USERS_RBAC, a -> {
            a.weaveByToUri(this.emailConnectorConfig.getRbacURL()).replace().setBody(new ConstantExpression(body));
        });

        // Make sure we replace the BOP endpoint too in order to check the
        // usernames we are sending.
        AdviceWith.adviceWith(this.context, Routes.SEND_EMAIL_BOP, a -> {
            a.weaveByToUri(String.format("%s*", this.emailConnectorConfig.getBopURL())).replace().to("mock:bopendpoint");
            a.mockEndpointsAndSkip(String.format("direct:%s", ConnectorToEngineRouteBuilder.SUCCESS));
        });

        // Set up the expected exchanges count that the success endpoint should
        // receive at the end of the test.
        AdviceWith.adviceWith(this.context, Routes.SEND_EMAIL_BOP_CHOICE, a -> {
            a.mockEndpoints(String.format("direct:%s", ConnectorToEngineRouteBuilder.SUCCESS));
        });

        final MockEndpoint successEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", ConnectorToEngineRouteBuilder.SUCCESS));
        successEndpoint.expectedMessageCount(1);

        // Send the exchange to the entry point of the email connector.
        this.producerTemplate.send(String.format("direct:%s", EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR), exchange);

        successEndpoint.assertIsSatisfied();

        // Get the exchanges that we sent to BOP. In theory, since we are
        // sending an email per user, we should receive many of them.
        final MockEndpoint bopEndpoint = this.getMockEndpoint("mock:bopendpoint");
        final List<Exchange> exchanges = bopEndpoint.getExchanges();

        Assertions.assertEquals(4, exchanges.size(), "unexpected number exchanges present in BOP's endpoint");

        for (final Exchange bopExchange : exchanges) {
            final String bopBody = bopExchange.getMessage().getBody(String.class);
            final JsonObject bopBodyJson = new JsonObject(bopBody);

            Assertions.assertNotNull(bopBodyJson, "the message body sent to BOP was empty");

            final JsonArray emails = bopBodyJson.getJsonArray("emails");
            Assertions.assertNotNull(emails, "the emails object of the BOP message was empty");

            final JsonObject email = emails.getJsonObject(0);
            Assertions.assertNotNull(email, "the email object to be sent to BOP is empty");

            final JsonArray bccList = email.getJsonArray("bccList");
            Assertions.assertNotNull(bccList, "the email's BCC List is empty");
            Assertions.assertEquals(1, bccList.size(), "the email contained more than one recipient when the single email per user option is enabled");

            final String username = bccList.getString(0);

            if (!users.contains(username) && !users2.contains(username)) {
                Assertions.fail(String.format("The username '%s' was not found in the original usernames lists. First users list: %s, second users list: %s", username, users, users2));
            }
        }
    }
}

package com.redhat.cloud.notifications.connector.email;

import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsFilterTest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Individual class for testing the bug specified below but with the "single
 * email for multiple users" option enabled.
 */
@QuarkusTest
public class EmailRouteBuilderRHCLOUD28631Test extends CamelQuarkusTestSupport {
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
            users,
            null
        );
        final Set<String> users2 = Set.of("johndoe", "janedoe");

        // Prepare the final expected list of usernames that we will be
        // comparing the generated email request to.
        final Set<String> finalUsersList = new HashSet<>();
        finalUsersList.addAll(users);
        finalUsersList.addAll(users2);

        final RecipientSettings recipientSettings2 = new RecipientSettings(
            true,
            true,
            null,
            users2,
            null
        );

        // Create the exchange that we will use in this test.
        final Exchange exchange = this.createExchangeWithBody("");

        final List<RecipientSettings> recipientSettingsList = new ArrayList<>();
        recipientSettingsList.add(recipientSettings);
        recipientSettingsList.add(recipientSettings2);

        final Set<String> subscribers = Set.of("user1", "user2");

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

        AdviceWith.adviceWith(this.context, Routes.SEND_EMAIL_BOP, a -> {
            // Make sure we replace the BOP endpoint too in order to check the
            // usernames we are sending.
            a.weaveByToUri(String.format("%s*", this.emailConnectorConfig.getBopURL())).replace().to("mock:bopendpoint");
            // Set up the expected exchanges count that the success endpoint should
            // receive at the end of the test.
            a.mockEndpoints(String.format("direct:%s", ConnectorToEngineRouteBuilder.SUCCESS));
        });

        final MockEndpoint successEndpoint = this.getMockEndpoint(String.format("mock:direct:%s", ConnectorToEngineRouteBuilder.SUCCESS), false);
        successEndpoint.expectedMessageCount(1);

        // Send the exchange to the entry point of the email connector.
        this.producerTemplate.send(String.format("seda:%s", EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR), exchange);

        // We need a timeout here because SEDA processes the exchange from a different thread and a race condition may happen.
        successEndpoint.assertIsSatisfied(2000L);

        // Get the exchanges that we sent to BOP. In theory, since we are
        // sending a single email for multiple users, we should only receive
        // one exchabnge here.
        final MockEndpoint bopEndpoint = this.getMockEndpoint("mock:bopendpoint", false);
        final List<Exchange> exchanges = bopEndpoint.getExchanges();

        Assertions.assertEquals(1, exchanges.size(), "unexpected number exchanges present in BOP's endpoint");

        final Exchange bopExchange = exchanges.get(0);

        final String bopBody = bopExchange.getMessage().getBody(String.class);
        final JsonObject bopBodyJson = new JsonObject(bopBody);

        Assertions.assertNotNull(bopBodyJson, "the message body sent to BOP was empty");

        final JsonArray emails = bopBodyJson.getJsonArray("emails");
        Assertions.assertNotNull(emails, "the emails object of the BOP message was empty");

        final JsonObject email = emails.getJsonObject(0);
        Assertions.assertNotNull(email, "the email object to be sent to BOP is empty");

        final JsonArray bccListJson = email.getJsonArray("bccList");
        Assertions.assertNotNull(bccListJson, "the email's BCC List is empty");

        final List<String> bccList = new ArrayList<>();
        for (final Object jsonObject : bccListJson.stream().toList()) {
            bccList.add((String) jsonObject);
        }

        // Check that the users are there.
        RecipientsFilterTest.assertUsernameCollectionsEqualsIgnoreOrder(finalUsersList, bccList);
    }
}

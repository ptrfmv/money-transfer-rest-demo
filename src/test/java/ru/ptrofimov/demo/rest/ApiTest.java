package ru.ptrofimov.demo.rest;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
import ru.ptrofimov.demo.model.MoneyTransferResponse;
import ru.ptrofimov.demo.model.MoneyTransferStatus;
import ru.ptrofimov.demo.utils.JettyUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.UUID;

import static ru.ptrofimov.demo.rest.MoneyTransferEntryPoint.*;
import static ru.ptrofimov.demo.rest.PathConstants.*;

public class ApiTest extends Assert {

    private static Server jettyServer;

    @BeforeClass
    public static void setUp() throws Exception {
        jettyServer = JettyUtils.createServer();

        jettyServer.start();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        jettyServer.stop();
        jettyServer.destroy();
    }

    @Test
    public void testGreeting() {
        Client client = ClientBuilder.newClient();
        String greeting = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + GREETING_PATH)
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);
        assertEquals(GREETING_TEXT, greeting);
    }

    @Test
    public void testCreateAndGetAccount() {
        Client client = ClientBuilder.newClient();

        Currency currency = Currency.AMERICAN_DOLLAR;
        BigDecimal balance = BigDecimal.valueOf(1200);
        String owner = UUID.randomUUID().toString();

        AccountDetails accountDetails = new AccountDetails(currency, balance, owner);

        accountDetails = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(accountDetails, MediaType.APPLICATION_JSON_TYPE), AccountDetails.class);
        long accountId = accountDetails.getId();

        accountDetails = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + accountId)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(AccountDetails.class);
        assertEquals(currency, accountDetails.getCurrency());
        //noinspection SimplifiableJUnitAssertion
        assertTrue(balance.compareTo(accountDetails.getBalance()) == 0); // equals won't work because of trailing zeros
        assertEquals(owner, accountDetails.getOwner());
    }

    @Test
    public void testTransferDifferentCurrencies() {
        Client client = ClientBuilder.newClient();

        Form form = new Form();
        form.param("from", Long.toString(2));
        form.param("amount", BigDecimal.ONE.toString());

        MoneyTransferResponse response = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + 3 + "/balance")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), MoneyTransferResponse.class);
        assertEquals(MoneyTransferStatus.CURRENCY_MISMATCH, response.getStatus());
    }

    @Test
    public void testTransferWithInsufficientFunds() {
        Client client = ClientBuilder.newClient();

        Form form = new Form();
        form.param("from", Long.toString(4));
        form.param("amount", String.valueOf(700));

        MoneyTransferResponse response = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + 1 + "/balance")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), MoneyTransferResponse.class);
        assertEquals(MoneyTransferStatus.INSUFFICIENT_FUNDS, response.getStatus());
    }

    @Test
    public void testTransferNegativeFunds() {
        Client client = ClientBuilder.newClient();

        Form form = new Form();
        form.param("from", Long.toString(4));
        form.param("amount", String.valueOf(-700));

        Response response = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + 1 + "/balance")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), Response.class);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testTransferMoney() {
        final long sender = 1; // 1000 RUR
        final long recipient = 4; // 500 RUR

        Client client = ClientBuilder.newClient();

        BigDecimal amount = BigDecimal.valueOf(500);
        Form form = new Form();
        form.param("from", Long.toString(sender));
        form.param("amount", amount.toString());

        MoneyTransferResponse response = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + recipient + "/balance")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), MoneyTransferResponse.class);
        assertEquals(MoneyTransferStatus.SUCCESS, response.getStatus());

        AccountDetails senderDetails = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + sender)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(AccountDetails.class);
        //noinspection SimplifiableJUnitAssertion
        assertTrue(BigDecimal.valueOf(500).compareTo(senderDetails.getBalance()) == 0);

        AccountDetails recipientDetails = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS + "/" + recipient)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(AccountDetails.class);
        //noinspection SimplifiableJUnitAssertion
        assertTrue(BigDecimal.valueOf(1000).compareTo(recipientDetails.getBalance()) == 0);
    }
}

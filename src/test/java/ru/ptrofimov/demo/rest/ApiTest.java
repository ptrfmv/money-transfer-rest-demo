package ru.ptrofimov.demo.rest;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
import ru.ptrofimov.demo.utils.JettyUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

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

        Form form = new Form();
        form.param("currency", currency.getShortName());
        form.param("balance", balance.toString());
        form.param("owner", owner);
        AccountDetails accountDetails = client.target("http://localhost:8080/" + API)
                .path(MONEY_TRANSFER_ENTRY_POINT + "/" + ACCOUNTS)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), AccountDetails.class);
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
}

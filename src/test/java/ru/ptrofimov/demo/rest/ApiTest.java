package ru.ptrofimov.demo.rest;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.ptrofimov.demo.utils.JettyUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

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
}

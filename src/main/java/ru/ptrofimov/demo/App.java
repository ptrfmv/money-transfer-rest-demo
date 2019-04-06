package ru.ptrofimov.demo;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.utils.JettyUtils;

/**
 * Money Transfer Demo Main Class.
 * Starts a Jetty instance for user to play freely with {@link ru.ptrofimov.demo.rest.MoneyTransferEntryPoint}.
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.trace("invoked main");

        Server jettyServer = JettyUtils.createServer();

        try {
            jettyServer.start();
            jettyServer.join();
        }catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        } finally {
            jettyServer.destroy();
        }
    }
}

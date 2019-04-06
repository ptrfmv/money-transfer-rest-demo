package ru.ptrofimov.demo.utils;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import ru.ptrofimov.demo.rest.MoneyTransferEntryPoint;
import ru.ptrofimov.demo.rest.PathConstants;

public final class JettyUtils {
    private JettyUtils() {
    }

    public static Server createServer() {
        ServletContextHandler context = new ServletContextHandler(0);
        context.setContextPath("/");

        Server jettyServer = new Server(8080);
        jettyServer.setHandler(context);

        ServletHolder jerseyServlet = context.addServlet(
                org.glassfish.jersey.servlet.ServletContainer.class, "/" + PathConstants.API + "/*");
        jerseyServlet.setInitOrder(0);

        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                MoneyTransferEntryPoint.class.getCanonicalName());
        return jettyServer;
    }
}

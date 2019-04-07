package ru.ptrofimov.demo.utils;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public class DBUtils {
    private DBUtils() {
    }

    private static boolean initialised;

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;";
        if (!initialised) {
            URL initSqlRes = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("init.sql"));
            url += "INIT=runscript from '" + initSqlRes + "'";
        }
        Connection connection = DriverManager.getConnection(url);
        initialised = true;
        return connection;
    }
}

package ru.ptrofimov.demo.logic;

import org.h2.api.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.exceptions.InsufficientFundsException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UpdateBalanceTrigger implements Trigger {

    private static final Logger logger = LoggerFactory.getLogger(UpdateBalanceTrigger.class);

    private int balanceColumn;

    @Override
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) throws SQLException {
        try (ResultSet resultSet = conn.getMetaData().getColumns("", schemaName, tableName, "BALANCE")) {
            resultSet.next();
            balanceColumn = resultSet.getInt("ORDINAL_POSITION");
        }
        logger.debug("trigger initialised, balance column = {}", balanceColumn);
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        validateBalance(oldRow[balanceColumn - 1], newRow[balanceColumn - 1]);
    }

    private void validateBalance(Object oldVal, Object newVal) {
        BigDecimal oldBalance = (BigDecimal) oldVal;
        BigDecimal newBalance = (BigDecimal) newVal;
        if (newBalance.compareTo(oldBalance) < 0 && newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException();
        }
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public void remove() throws SQLException {

    }
}

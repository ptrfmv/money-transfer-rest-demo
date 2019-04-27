package ru.ptrofimov.demo.logic;

import ru.ptrofimov.demo.exceptions.AccountNotFoundException;
import ru.ptrofimov.demo.exceptions.CurrencyMismatchException;
import ru.ptrofimov.demo.exceptions.InsufficientFundsException;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
import ru.ptrofimov.demo.utils.DBUtils;

import java.math.BigDecimal;
import java.sql.*;

public class AccountHelper implements AutoCloseable {

    private Connection connection;

    public AccountHelper() throws SQLException {
        this.connection = DBUtils.getConnection();
    }

    public AccountDetails createAccount(Currency currency, BigDecimal balance, String owner) throws SQLException {
        long insertedId;
        connection.setAutoCommit(true);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ACCOUNTS (CURRENCY, BALANCE, OWNER) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, currency.getShortName());
            statement.setBigDecimal(2, balance);
            statement.setString(3, owner);
            statement.executeUpdate();
            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                resultSet.next();
                insertedId = resultSet.getLong(1);
            }
        }
        AccountDetails result = new AccountDetails();
        result.setId(insertedId);
        return result;
    }

    public AccountDetails getAccountDetails(long accountId) throws SQLException, AccountNotFoundException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT CURRENCY, BALANCE, OWNER FROM ACCOUNTS WHERE ID = ?")) {
            statement.setLong(1, accountId);
            statement.execute();
            try (ResultSet resultSet = statement.getResultSet()) {
                if (resultSet.next()) {
                    AccountDetails result = new AccountDetails();
                    result.setCurrency(Currency.fromString(resultSet.getString(1)));
                    result.setBalance(resultSet.getBigDecimal(2));
                    result.setOwner(resultSet.getString(3));
                    result.setId(accountId);
                    return result;
                }
            }
        }
        throw new AccountNotFoundException(accountId);
    }

    public void transferMoney(long senderId, long recipientId, BigDecimal amount) throws SQLException, AccountNotFoundException, CurrencyMismatchException {
        connection.setAutoCommit(false);

        AccountDetails recipientAccountDetails = getAccountDetails(recipientId);
        AccountDetails senderAccountDetails = getAccountDetails(senderId);

        if (recipientAccountDetails.getCurrency() != senderAccountDetails.getCurrency()) {
            throw new CurrencyMismatchException();
        }

        try {
            updateBalances(senderId, recipientId, amount);
            connection.commit();
        } catch (SQLNonTransientException sqlException) {
            connection.rollback();
            Throwable cause = sqlException.getCause();
            if (cause instanceof InsufficientFundsException) {
                throw (InsufficientFundsException) cause;
            } else {
                throw sqlException;
            }
        } catch (Exception e) {
            connection.rollback();
            throw e;
        }
    }

    private void updateBalances(long senderId, long recipientId, BigDecimal amount) throws SQLException {
        long first, second;
        BigDecimal firstAmount, secondAmount;
        if (senderId <= recipientId) {
            first = senderId;
            second = recipientId;
            firstAmount = amount.negate();
            secondAmount = amount;
        } else {
            first = recipientId;
            second = senderId;
            firstAmount = amount;
            secondAmount = amount.negate();
        }
        updateBalance(first, firstAmount);
        updateBalance(second, secondAmount);
    }

    private void updateBalance(long accountId, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("UPDATE ACCOUNTS SET BALANCE = BALANCE + ? WHERE ID = ?")) {
            statement.setBigDecimal(1, amount);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        }
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}

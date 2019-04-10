package ru.ptrofimov.demo.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ptrofimov.demo.model.AccountDetails;
import ru.ptrofimov.demo.model.Currency;
import ru.ptrofimov.demo.model.MoneyTransferResponse;
import ru.ptrofimov.demo.model.MoneyTransferStatus;
import ru.ptrofimov.demo.utils.DBUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;

import static ru.ptrofimov.demo.rest.PathConstants.*;

@Path("/" + MONEY_TRANSFER_ENTRY_POINT)
public class MoneyTransferEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(MoneyTransferEntryPoint.class);

    static final String GREETING_TEXT = "I'm a lean mean money transferring machine";

    @GET
    @Path(GREETING_PATH)
    @Produces(MediaType.TEXT_PLAIN)
    public String greet() {
        return GREETING_TEXT;
    }

    @POST
    @Path(ACCOUNTS)
    @Produces(MediaType.APPLICATION_JSON)
    public AccountDetails createAccount(@FormParam("currency") Currency currency,
                                        @FormParam("balance") BigDecimal balance,
                                        @FormParam("owner") String owner) throws SQLException {
        try {
            Objects.requireNonNull(currency);
            Objects.requireNonNull(balance);
            Objects.requireNonNull(owner);
            long insertedId;
            try (Connection connection = DBUtils.getConnection()) {
                logger.trace("inserting currency = {} balance = {} owner = {}", currency, balance, owner);
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
            }
            AccountDetails result = new AccountDetails();
            result.setId(insertedId);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @GET
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountDetails(@PathParam("accountId") long accountId) throws SQLException {
        try {
            AccountDetails result;
            try (Connection connection = DBUtils.getConnection()) {
                result = getAccountDetails(connection, accountId);
            }
            if (result == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(result).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static AccountDetails getAccountDetails(Connection connection, long accountId) throws SQLException {
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
                    return result;
                }
            }
        }
        return null;
    }

    @POST
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response transferMoney(@PathParam("accountId") long recipientId,
                                  @FormParam("from") long senderId,
                                  @FormParam("amount") BigDecimal amount) throws SQLException {
        if (recipientId == 0 || senderId == 0 || recipientId == senderId ||
                amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            try (Connection connection = DBUtils.getConnection()) {
                connection.setAutoCommit(false);
                AccountDetailsWithVersion recipientAccountDetails = getAccountDetailsWithVersion(connection, recipientId);
                if (recipientAccountDetails == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                recipientAccountDetails.setId(recipientId);

                AccountDetailsWithVersion senderAccountDetails = getAccountDetailsWithVersion(connection, senderId);
                if (senderAccountDetails == null) {
                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.ACCOUNT_NOT_FOUND)).build();
                }
                senderAccountDetails.setId(senderId);

                if (recipientAccountDetails.getCurrency() != senderAccountDetails.getCurrency()) {
                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.CURRENCY_MISMATCH)).build();
                }

                BigDecimal subtract = senderAccountDetails.getBalance().subtract(amount);
                if (subtract.compareTo(BigDecimal.ZERO) < 0) {
                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.INSUFFICIENT_FUNDS)).build();
                }

                senderAccountDetails.setBalance(subtract);
                recipientAccountDetails.setBalance(recipientAccountDetails.getBalance().add(amount));

                AccountDetailsWithVersion first, second;
                if (senderId <= recipientId) {
                    first = senderAccountDetails;
                    second = recipientAccountDetails;
                } else {
                    first = recipientAccountDetails;
                    second = senderAccountDetails;
                }
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                if (updateBalance(connection, first) && updateBalance(connection, second)) {
                    connection.commit();
                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.SUCCESS)).build();
                } else {
                    connection.rollback();
                    return Response.status(Response.Status.CONFLICT).build();
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static AccountDetailsWithVersion getAccountDetailsWithVersion(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT CURRENCY, BALANCE, OWNER, VERSION FROM ACCOUNTS WHERE ID = ?")) {
            statement.setLong(1, accountId);
            statement.execute();
            try (ResultSet resultSet = statement.getResultSet()) {
                if (resultSet.next()) {
                    AccountDetailsWithVersion result = new AccountDetailsWithVersion();
                    result.setCurrency(Currency.fromString(resultSet.getString(1)));
                    result.setBalance(resultSet.getBigDecimal(2));
                    result.setOwner(resultSet.getString(3));
                    result.setVersion(resultSet.getInt(4));
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean updateBalance(Connection connection, AccountDetailsWithVersion accountDetails) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("UPDATE ACCOUNTS SET BALANCE = ?, VERSION = VERSION + 1 WHERE ID = ? AND VERSION = ?")) {
            statement.setBigDecimal(1, accountDetails.getBalance());
            statement.setLong(2, accountDetails.getId());
            statement.setInt(3, accountDetails.getVersion());
            return statement.executeUpdate() == 1;
        }
    }

    private static class AccountDetailsWithVersion extends AccountDetails {
        private int version;

        int getVersion() {
            return version;
        }

        void setVersion(int version) {
            this.version = version;
        }
    }
}

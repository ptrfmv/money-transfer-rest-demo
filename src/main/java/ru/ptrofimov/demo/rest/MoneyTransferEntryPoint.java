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
import java.util.*;

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
            logger.error(e.getMessage() ,e);
            throw e;
        }
    }

    @GET
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountDetails(@PathParam("accountId") long accountId) throws SQLException {
        try {
            AccountDetails result = null;
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

    private static final Map<AccountLockKey, Object> LOCK_POOL = Collections.synchronizedMap(new WeakHashMap<>());

    @POST
    @Path(ACCOUNTS + "/{accountId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response transferMoney(@PathParam("accountId") long recipientId,
                                  @FormParam("from") long senderId,
                                  @FormParam("amount") BigDecimal amount) throws SQLException {
        try {
            try (Connection connection = DBUtils.getConnection()) {
                connection.setAutoCommit(false);
                long firstAccId = Math.min(senderId, recipientId);
                long secondAccId = Math.max(senderId, recipientId);
                AccountLockKey lockKey = new AccountLockKey(firstAccId, secondAccId);
                synchronized (LOCK_POOL.computeIfAbsent(lockKey, key -> new Object())) {
                    AccountDetails recipientAccountDetails = getAccountDetails(connection, recipientId);
                    if (recipientAccountDetails == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }

                    AccountDetails senderAccountDetails = getAccountDetails(connection, senderId);
                    if (senderAccountDetails == null) {
                        return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.ACCOUNT_NOT_FOUND)).build();
                    }

                    if (recipientAccountDetails.getCurrency() != senderAccountDetails.getCurrency()) {
                        return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.CURRENCY_MISMATCH)).build();
                    }

                    BigDecimal subtract = senderAccountDetails.getBalance().subtract(amount);
                    if (subtract.compareTo(BigDecimal.ZERO) < 0) {
                        return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.INSUFFICIENT_FUNDS)).build();
                    }

                    updateBalance(connection, senderId, subtract);
                    updateBalance(connection, recipientId, recipientAccountDetails.getBalance().add(amount));

                    connection.commit();

                    return Response.ok(new MoneyTransferResponse(MoneyTransferStatus.SUCCESS)).build();
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private static void updateBalance(Connection connection, long accountId, BigDecimal balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE ACCOUNTS SET BALANCE = ? WHERE ID = ?")) {
            statement.setBigDecimal(1, balance);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        }
    }

    private static final class AccountLockKey {
        private long[] accountIds;

        AccountLockKey(long... accountIds) {
            this.accountIds = accountIds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AccountLockKey that = (AccountLockKey) o;
            return Arrays.equals(accountIds, that.accountIds);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(accountIds);
        }
    }
}
